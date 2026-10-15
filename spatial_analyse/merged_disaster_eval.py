"""
融合版灾害判别（TRIGRS + CRI 滑坡 · 暴雨 · 高温 · 干旱）
=========================================================
数据流向: 读 gis.gis_weather_daily → 算 → 写 biz.biz_disaster_eval (幂等)

列映射:
  landslide_level  ← CRI 等级 (1低/2中/3高/4极高)
  mudslide_level   ← 暴雨等级 (GB/T 28592-2012)
  freezethaw_level ← 高温热浪等级 (GB/T 20481-2017)
  collapse_level   ← 干旱等级 (干/湿季分档)

comp_level = max(四列)

用法:
  python merged_disaster_eval.py                     # 全部年份 (幂等)
  python merged_disaster_eval.py --years 2020,2022   # 指定年份
  python merged_disaster_eval.py --station 56571     # 单站
"""

import argparse
import math
import os
import sys
import gc
import logging

import numpy as np
import pandas as pd
import psycopg2
import psycopg2.extras
from scipy.spatial import KDTree

# ====================== 配置 ======================
DB_DSN = "host=localhost port=5432 dbname=gcsj user=gcsj password=gcsj123"
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(BASE_DIR)
DATA_DIR = os.path.join(PROJECT_ROOT, "data")

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(levelname)s - %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S"
)
logger = logging.getLogger("MergedDisasterEval")

# ====================== TRIGRS 岩土参数 ======================
c_prime = 12.0
phi_prime = np.radians(28.0)
gamma_s = 19.0
gamma_w = 9.81
gamma_t = 17.2
soil_thickness_z = 2.0
Ks = 0.05
PRECIP_GATE = 0.005
ALPHA = 0.85
WINDOW = 15

# ====================== 通用工具 ======================
def jround(v: float, scale: int) -> float:
    f = 10 ** scale
    return math.floor(v * f + 0.5) / f

def nz(v):
    return 0.0 if v is None else float(v)

# ====================== TRIGRS + CRI ======================
def calc_KT(T):
    T = np.asarray(T, dtype=float)
    return np.select([T < 0, T <= 26], [0.22, 1.0 - 0.011 * T], default=0.62)

def elevation_susceptibility(dem):
    dem = float(dem)
    if 500 <= dem < 1500: return 0.9
    elif 1500 <= dem < 3000: return 0.6
    elif dem < 500: return 0.1
    else: return 0.2

def effective_rainfall(rainfall_series: list, t: int) -> float:
    total = 0.0
    for i in range(1, WINDOW + 1):
        idx = t - i
        if idx < 0: break
        total += (ALPHA ** i) * (rainfall_series[idx] or 0.0)
    return total

def calc_trigrs_fs(precip_mm: float, temp_c: float, slope_deg: float) -> float:
    Rr = precip_mm / 1000.0
    slope_rad = np.radians(slope_deg)
    Re = Rr * float(calc_KT(temp_c))
    if Re < PRECIP_GATE: return 999.0
    if Re >= 1e-8:
        h = (Re / Ks) * (1 - math.exp(-Ks * soil_thickness_z / Re))
    else:
        h = 0.0
    h = max(0.0, min(soil_thickness_z, h))
    cos_s = math.cos(slope_rad)
    sin_s = math.sin(slope_rad)
    numer = c_prime + (gamma_s - gamma_w * h) * (cos_s ** 2) * math.tan(phi_prime)
    denom = gamma_t * soil_thickness_z * sin_s * cos_s
    if abs(denom) < 1e-10: return 999.0
    return numer / denom

def calc_cri(fs: float, slope_deg: float, precip_mm: float, dem: float) -> float:
    if fs >= 999: fs_score = 0.0
    else:
        fs_clipped = max(0.1, min(50.0, fs))
        fs_score = 1.0 / (1.0 + math.exp(8.0 * (fs_clipped - 1.1)))
    slope_score = max(0.0, min(1.0, (slope_deg - 10.0) / 30.0))
    precip_score = max(0.0, min(1.0, (precip_mm - 10.0) / 40.0))
    elev_score = elevation_susceptibility(dem)
    return 0.40 * fs_score + 0.25 * slope_score + 0.20 * precip_score + 0.15 * elev_score

def fs_to_landslide_level(fs: float) -> int:
    if fs >= 999: return 0
    if fs < 1.0: return 4
    if fs < 1.2: return 3
    if fs < 1.5: return 2
    if fs < 2.0: return 1
    return 0

# ====================== 气象灾害判别 ======================
def rainstorm_level(rainfall) -> int:
    if rainfall is None: return 0
    r = float(rainfall)
    if r >= 250: return 4
    if r >= 100: return 3
    if r >= 50: return 2
    if r >= 25: return 1
    return 0

def heatwave_level(temp_max) -> int:
    if temp_max is None: return 0
    t = float(temp_max)
    if t >= 40: return 4
    if t >= 37: return 3
    if t >= 35: return 2
    if t >= 33: return 1
    return 0

DRY_MONTHS = {11, 12, 1, 2, 3, 4}
WET_MONTHS = {5, 6, 7, 8, 9, 10}
DRY_DROUGHT_THRESHOLDS = [(30, 1), (20, 2), (10, 3), (5, 4)]
WET_DROUGHT_THRESHOLDS = [(80, 1), (60, 2), (40, 3), (20, 4)]

def _drought_level(month: int, monthly_rain: float, dry_ratio: float) -> int:
    is_dry = month in DRY_MONTHS
    thresholds = DRY_DROUGHT_THRESHOLDS if is_dry else WET_DROUGHT_THRESHOLDS
    for thresh, lv in reversed(thresholds):
        if monthly_rain < thresh:
            if is_dry and lv == 4 and dry_ratio <= 0.9:
                return 3 if monthly_rain < thresholds[-2][0] else 0
            return lv
    return 0

# ====================== 栅格读取 ======================
def read_raster_csv(file_path: str) -> pd.DataFrame:
    df = pd.read_csv(file_path)
    df.rename(columns={"X": "lon", "Y": "lat", "VALUE": "val"}, inplace=True)
    return df

# ====================== 逐站评估 ======================
def evaluate_station(records: list, slope_deg: float, dem: float) -> list:
    """
    records: [{station_code, obs_date, rainfall, temp_avg, temp_max, temp_min}, ...] 按日期升序
    """
    rainfalls = [nz(r.get("rainfall")) for r in records]
    temps = [nz(r.get("temp_avg")) for r in records]
    tmaxs = [r.get("temp_max") for r in records]
    tmins = [r.get("temp_min") for r in records]

    # 干旱预计算：逐月累计
    monthly_rain = {}
    monthly_days = {}
    monthly_dry = {}
    for r in records:
        dt = r["obs_date"]
        ym = (dt.year, dt.month)
        rain = nz(r.get("rainfall"))
        monthly_rain[ym] = monthly_rain.get(ym, 0.0) + rain
        monthly_days[ym] = monthly_days.get(ym, 0) + 1
        if rain < 0.1:
            monthly_dry[ym] = monthly_dry.get(ym, 0) + 1

    results = []
    for t, cur in enumerate(records):
        r_eff = effective_rainfall(rainfalls, t)

        tmax_v, tmin_v = tmaxs[t], tmins[t]
        dtr = (float(tmax_v) - float(tmin_v)) if (tmax_v is not None and tmin_v is not None) else None

        rain_v = rainfalls[t]
        temp_v = temps[t]
        dt = cur["obs_date"]
        code = cur["station_code"]

        # 滑坡 → TRIGRS
        fs = calc_trigrs_fs(rain_v, temp_v, slope_deg)
        cri = calc_cri(fs, slope_deg, rain_v, dem)
        ls = fs_to_landslide_level(fs)

        # 暴雨
        rs = rainstorm_level(rain_v)

        # 高温
        hw = heatwave_level(tmax_v)

        # 干旱
        ym = (dt.year, dt.month)
        mrain = monthly_rain.get(ym, 0.0)
        mdays = max(1, monthly_days.get(ym, 1))
        dry_ratio = monthly_dry.get(ym, 0) / mdays
        dr = _drought_level(dt.month, mrain, dry_ratio)

        # CRI → 0-4 等级
        if cri >= 0.35: cri_lv = 4
        elif cri >= 0.28: cri_lv = 3
        elif cri >= 0.22: cri_lv = 2
        else: cri_lv = 1

        # 映射到已有列:
        #   landslide_level  ← cri_lv  (CRI)
        #   mudslide_level   ← rs      (暴雨)
        #   freezethaw_level ← hw      (高温)
        #   collapse_level   ← dr      (干旱)

        comp = max(cri_lv, rs, hw, dr)

        results.append((
            code, dt,
            jround(r_eff, 2),
            None if dtr is None else jround(dtr, 2),
            cri_lv, rs, hw, dr, comp,
        ))

    return results


UPSERT_SQL = """
    INSERT INTO biz.biz_disaster_eval
        (station_code, obs_date, r_eff, dtr,
         landslide_level, mudslide_level, freezethaw_level, collapse_level,
         comp_level)
    VALUES %s
    ON CONFLICT (station_code, obs_date) DO UPDATE SET
        r_eff = EXCLUDED.r_eff,
        dtr = EXCLUDED.dtr,
        landslide_level  = EXCLUDED.landslide_level,
        mudslide_level   = EXCLUDED.mudslide_level,
        freezethaw_level = EXCLUDED.freezethaw_level,
        collapse_level   = EXCLUDED.collapse_level,
        comp_level       = EXCLUDED.comp_level
"""


def parse_args():
    p = argparse.ArgumentParser(description="融合版灾害判别 (TRIGRS滑坡 + 暴雨/高温/干旱)")
    p.add_argument("--years", default=None, help="逗号分隔年份, 如 2020,2021")
    p.add_argument("--station", default=None, help="只算指定区站号")
    return p.parse_args()


def main():
    args = parse_args()
    years = [int(y.strip()) for y in args.years.split(",")] if args.years else None

    # ---- 1. 加载地形栅格 ----
    dem_path = os.path.join(DATA_DIR, "dem_sc.csv")
    slope_path = os.path.join(DATA_DIR, "slope_sc.csv")
    for p, name in [(dem_path, "DEM"), (slope_path, "Slope")]:
        if not os.path.exists(p):
            logger.error(f"{name} 文件不存在: {p}")
            sys.exit(1)

    logger.info("读取地形栅格...")
    dem_df = read_raster_csv(dem_path)
    slope_df = read_raster_csv(slope_path)
    dem_kd = KDTree(dem_df[["lon", "lat"]].values); dem_vals = dem_df["val"].values
    slope_kd = KDTree(slope_df[["lon", "lat"]].values); slope_vals = slope_df["val"].values
    logger.info(f"DEM: {len(dem_df)} 网格, Slope: {len(slope_df)} 网格")

    # ---- 2. 从 DB 读取气象数据 ----
    where = " WHERE 1=1 "
    params = []
    if args.station:
        where += " AND station_code = %s "
        params.append(args.station)
    if years:
        where += " AND EXTRACT(YEAR FROM obs_date)::int = ANY(%s) "
        params.append(years)

    sql = ("SELECT station_code, obs_date, rainfall, temp_avg, temp_max, temp_min "
           "FROM gis.gis_weather_daily" + where +
           " ORDER BY station_code, obs_date")

    logger.info("[1/4] 连接数据库并读取日观测...")
    conn = psycopg2.connect(DB_DSN)
    conn.autocommit = False
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(sql, params)
            rows = cur.fetchall()
        logger.info(f"   读到 {len(rows)} 行"
                    f"{' (年份 ' + str(years) + ')' if years else ' (全部年份)'}")

        # 按站点分组 (已按 station_code, obs_date 升序)
        by_station = {}
        for r in rows:
            by_station.setdefault(r["station_code"], []).append(r)

        # 从 station 表获取每个站点经纬度
        station_codes = list(by_station.keys())
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(
                "SELECT code, ST_X(location) AS lon, ST_Y(location) AS lat "
                "FROM gis.gis_weather_station WHERE code = ANY(%s)",
                [station_codes]
            )
            station_loc = {r["code"]: (r["lon"], r["lat"]) for r in cur.fetchall()}

        logger.info(f"[2/4] 逐站判别 ({len(by_station)} 站) + 提取地形...")
        all_records = []
        n_skipped = 0
        RADIUS = 0.15  # ~15km

        for i, (code, series) in enumerate(by_station.items()):
            if (i + 1) % 50 == 0:
                logger.info(f"  进度: {i+1}/{len(by_station)} 站")

            # 获取站点坐标
            loc = station_loc.get(code)
            if loc is None:
                n_skipped += 1; continue
            lon, lat = loc

            # 从栅格提取坡度 (15km P75) + 海拔 (中位数)
            ns = slope_kd.query_ball_point([[lon, lat]], r=RADIUS)[0]
            nd = dem_kd.query_ball_point([[lon, lat]], r=RADIUS)[0]
            if len(ns) < 5 or len(nd) < 5:
                n_skipped += 1; continue

            slope_s = float(np.percentile(slope_vals[ns], 75))
            dem_s = float(np.median(dem_vals[nd]))

            try:
                daily = evaluate_station(series, slope_s, dem_s)
                all_records.extend(daily)
            except Exception as e:
                logger.warning(f"站点 {code} 评估失败: {e}")
                n_skipped += 1

        logger.info(f"评估完成: {len(all_records)} 条记录, 跳过 {n_skipped} 站")

        # ---- 3. Upsert 回 biz_disaster_eval ----
        logger.info(f"[3/4] Upsert 回 biz.biz_disaster_eval ({len(all_records)} 行)...")
        with conn.cursor() as cur:
            psycopg2.extras.execute_values(cur, UPSERT_SQL, all_records, page_size=2000)
        conn.commit()

        # ---- 4. 验收 ----
        logger.info("[4/4] 验收 (comp_level = max 四列):")
        with conn.cursor() as cur:
            cur.execute("SELECT EXTRACT(YEAR FROM obs_date)::int AS yr, COUNT(*) "
                        "FROM biz.biz_disaster_eval GROUP BY yr ORDER BY yr")
            for yr, cnt in cur.fetchall():
                logger.info(f"   {yr}: {cnt} 行")

            for col, label in [("landslide_level", "CRI"), ("mudslide_level", "暴雨"),
                               ("freezethaw_level", "高温"), ("collapse_level", "干旱"),
                               ("comp_level", "综合")]:
                for lv in range(1, 5):
                    cur.execute(f"SELECT COUNT(*) FROM biz.biz_disaster_eval WHERE {col} = {lv}")
                    cnt = cur.fetchone()[0]
                    if cnt > 0:
                        logger.info(f"   {label} Lv{lv}: {cnt}")

    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()

    logger.info("\n======== 全部完成 ========")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n中断")
        sys.exit(130)
