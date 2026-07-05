"""
灾害判别批算 (Python 版)。

读 gis.gis_weather_daily -> 逐站跑判别引擎 -> upsert 回 biz.biz_disaster_eval (幂等)。

判别类型（五类气象灾害，统一四色预警等级 1~4）：
  暴雨      - GB/T 28592-2012 降水量等级
  高温热浪  - GB/T 20481-2017 高温热浪等级
  寒潮      - GB/T 21987-2017 寒潮等级
  干旱      - GB/T 20481-2017 气象干旱等级（月累计简化方案）
  森林火险  - LY/T 1172-95 + QX/T 77-2007 森林火险气象等级

数据库表须包含字段：
  rainstorm_level, heatwave_level, coldwave_level,
  drought_level, fire_risk_level, comp_level, comp_index

依赖: psycopg2-binary
用法:
  python run_disaster_eval.py                     # 全部年份 (幂等)
  python run_disaster_eval.py --years 2020,2021,2023
  python run_disaster_eval.py --station 56571      # 只算单站
"""
import argparse
import sys

import psycopg2
import psycopg2.extras

# ---------------- 配置 ----------------
DB_DSN = "host=localhost port=5432 dbname=gcsj user=gcsj password=gcsj123"

# ---------------- 灾害判别等级体系 ----------------
# 统一四色预警：蓝色(1) < 黄色(2) < 橙色(3) < 红色(4)，0 = 无风险
#
# 依据标准：
#   暴雨    - GB/T 28592-2012 《降水量等级》
#   高温热浪 - GB/T 20481-2017 《高温热浪等级》
#   寒潮    - GB/T 21987-2017 《寒潮等级》
#   干旱    - GB/T 20481-2017 《气象干旱等级》（简化方案）
#   森林火险 - LY/T 1172-95 + QX/T 77-2007 《森林火险气象等级》

import math


def nz(v):
    """None -> 0.0"""
    return 0.0 if v is None else float(v)


def jround(v: float, scale: int) -> float:
    """对齐 Java Math.round (half-up)"""
    f = 10 ** scale
    return math.floor(v * f + 0.5) / f


# ---- §4.1 暴雨（GB/T 28592-2012）----
def rainstorm_level(rainfall) -> int:
    """
    蓝色(1): 大雨    25 ~ 50 mm
    黄色(2): 暴雨    50 ~ 100 mm
    橙色(3): 大暴雨  100 ~ 250 mm
    红色(4): 特大暴雨  ≥ 250 mm
    """
    if rainfall is None:
        return 0
    r = float(rainfall)
    if r >= 250:
        return 4
    if r >= 100:
        return 3
    if r >= 50:
        return 2
    if r >= 25:
        return 1
    return 0


# ---- §4.2 高温热浪（GB/T 20481-2017）----
def heatwave_level(temp_max) -> int:
    """
    蓝色(1): 正常偏高 33 ~ 35 °C
    黄色(2): 高温     35 ~ 37 °C
    橙色(3): 酷热     37 ~ 40 °C
    红色(4): 极端高温   ≥ 40 °C
    """
    if temp_max is None:
        return 0
    t = float(temp_max)
    if t >= 40:
        return 4
    if t >= 37:
        return 3
    if t >= 35:
        return 2
    if t >= 33:
        return 1
    return 0


# ---- §4.3 寒潮（GB/T 21987-2017）----
def coldwave_level(series, t: int) -> int:
    """
    基于相邻日最低气温 24h 降幅：
    蓝色(1): 强降温  6 ~ 8 °C
    黄色(2): 寒潮    8 ~ 10 °C
    橙色(3): 强寒潮  10 ~ 12 °C
    红色(4): 特强寒潮 ≥ 12 °C
    """
    if t == 0:
        return 0
    cur_tmin = series[t].get("temp_min")
    prev_tmin = series[t - 1].get("temp_min")
    if cur_tmin is None or prev_tmin is None:
        return 0
    drop = float(prev_tmin) - float(cur_tmin)
    if drop >= 12:
        return 4
    if drop >= 10:
        return 3
    if drop >= 8:
        return 2
    if drop >= 6:
        return 1
    return 0


# ---- §4.4 干旱（GB/T 20481-2017，月累计简化方案）----
# 由于仅有单年数据无法计算多年降水距平（Pa），采用干/湿季分档阈值。
# 在 evaluate_station 中预先计算各站-月累计降水与无雨日占比，逐日填充同一月内所有天相同等级。

# 干季月 (11,12,1,2,3,4) 与 湿季月 (5,6,7,8,9,10) 分档
DRY_MONTHS = {11, 12, 1, 2, 3, 4}
WET_MONTHS = {5, 6, 7, 8, 9, 10}

# 干季阈值 (月累计降水 mm)
DRY_DROUGHT_THRESHOLDS = [
    (30, 1),   # 蓝色(1): 轻旱, 月降水 < 30mm
    (20, 2),   # 黄色(2): 中旱, 月降水 < 20mm
    (10, 3),   # 橙色(3): 重旱, 月降水 < 10mm
    (5, 4),    # 红色(4): 特旱, 月降水 < 5mm 且无雨日 > 90%
]
# 湿季阈值
WET_DROUGHT_THRESHOLDS = [
    (80, 1),   # 蓝色(1): 轻旱
    (60, 2),   # 黄色(2): 中旱
    (40, 3),   # 橙色(3): 重旱
    (20, 4),   # 红色(4): 特旱
]


def _drought_level(month: int, monthly_rain: float, dry_ratio: float) -> int:
    """根据月份、月累计降水、无雨日占比返回干旱等级 0~4"""
    thresholds = DRY_DROUGHT_THRESHOLDS if month in DRY_MONTHS else WET_DROUGHT_THRESHOLDS
    for thresh, lv in reversed(thresholds):
        if monthly_rain < thresh:
            # 最高档 (红色) 额外要求无雨日 > 90%
            if lv == 4 and dry_ratio <= 0.9:
                return 3 if monthly_rain < thresholds[-2][0] else 0
            return lv
    return 0


# ---- §4.5 森林火险（LY/T 1172-95 + QX/T 77-2007）----
def fire_risk_level(temp_max, rh_avg, wind_max) -> int:
    """
    基于气温-湿度-风速组合（火险三角）：
    蓝色(1): 较低火险  Tmax≥20, RH≤45%,  Wind≥3.3 m/s
    黄色(2): 较高火险  Tmax≥25, RH≤35%,  Wind≥5.5 m/s
    橙色(3): 高火险    Tmax≥28, RH≤25%,  Wind≥8.0 m/s
    红色(4): 极高火险  Tmax≥30, RH≤15%,  Wind≥10.8 m/s
    """
    if temp_max is None or rh_avg is None or wind_max is None:
        return 0
    t = float(temp_max)
    rh = float(rh_avg)
    w = float(wind_max)
    if t >= 30 and rh <= 15 and w >= 10.8:
        return 4
    if t >= 28 and rh <= 25 and w >= 8.0:
        return 3
    if t >= 25 and rh <= 35 and w >= 5.5:
        return 2
    if t >= 20 and rh <= 45 and w >= 3.3:
        return 1
    return 0


# ---- 综合评估 ----
def evaluate_station(series):
    """series: 按 obs_date 升序的日观测 dict 列表 -> 判别结果 tuple 列表"""
    # 预计算：逐月累计降水 & 无雨日统计（干旱是月级别灾害，同月所有天等级一致）
    monthly_rain = {}   # (year, month) -> total_rainfall
    monthly_days = {}   # (year, month) -> total_days_in_data
    monthly_dry = {}    # (year, month) -> days_with_rainfall < 0.1

    for d in series:
        dt = d["obs_date"]
        ym = (dt.year, dt.month)
        r = nz(d["rainfall"])
        monthly_rain[ym] = monthly_rain.get(ym, 0.0) + r
        monthly_days[ym] = monthly_days.get(ym, 0) + 1
        if r < 0.1:
            monthly_dry[ym] = monthly_dry.get(ym, 0) + 1

    out = []
    for t, cur in enumerate(series):
        dt = cur["obs_date"]
        ym = (dt.year, dt.month)

        # 干旱等级：同月内所有天一致
        mrain = monthly_rain.get(ym, 0.0)
        mdays = max(1, monthly_days.get(ym, 1))
        dry_ratio = monthly_dry.get(ym, 0) / mdays
        dr = _drought_level(dt.month, mrain, dry_ratio)

        rs = rainstorm_level(cur.get("rainfall"))
        hw = heatwave_level(cur.get("temp_max"))
        cw = coldwave_level(series, t)
        fr = fire_risk_level(cur.get("temp_max"), cur.get("rh_avg"), cur.get("wind_max"))

        # 综合等级：取五种灾害的最高等级
        comp = max(rs, hw, cw, dr, fr)
        # 综合指数：归一化到 0~1
        hi = comp / 4.0

        out.append((
            cur["station_code"], cur["obs_date"],
            rs, hw, cw, dr, fr, comp, jround(hi, 3),
        ))
    return out


UPSERT = """
    INSERT INTO biz.biz_disaster_eval
        (station_code, obs_date,
         rainstorm_level, heatwave_level, coldwave_level,
         drought_level, fire_risk_level, comp_level, comp_index)
    VALUES %s
    ON CONFLICT (station_code, obs_date) DO UPDATE SET
        rainstorm_level = EXCLUDED.rainstorm_level,
        heatwave_level  = EXCLUDED.heatwave_level,
        coldwave_level  = EXCLUDED.coldwave_level,
        drought_level   = EXCLUDED.drought_level,
        fire_risk_level = EXCLUDED.fire_risk_level,
        comp_level      = EXCLUDED.comp_level,
        comp_index      = EXCLUDED.comp_index
"""


def parse_args():
    p = argparse.ArgumentParser(description="灾害判别批算 (等价 /run)")
    p.add_argument("--years", default=None, help="逗号分隔年份, 如 2020,2021,2023; 缺省全部")
    p.add_argument("--station", default=None, help="只算指定区站号")
    return p.parse_args()


def main():
    args = parse_args()
    years = [int(y.strip()) for y in args.years.split(",")] if args.years else None

    where = " WHERE 1=1 "
    params = []
    if args.station:
        where += " AND station_code = %s "
        params.append(args.station)
    if years:
        where += " AND EXTRACT(YEAR FROM obs_date)::int = ANY(%s) "
        params.append(years)

    sql = ("SELECT station_code, obs_date, rainfall, temp_max, temp_min, rh_avg, wind_max "
           "FROM gis.gis_weather_daily" + where +
           " ORDER BY station_code, obs_date")

    print("[1/3] 连接数据库并读取日观测 ...")
    conn = psycopg2.connect(DB_DSN)
    conn.autocommit = False
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(sql, params)
            rows = cur.fetchall()
        print(f"   读到 {len(rows)} 行"
              f"{' (年份 ' + str(years) + ')' if years else ' (全部年份)'}")

        # 按站点分组 (已按 station_code, obs_date 升序)
        by_station = {}
        for r in rows:
            by_station.setdefault(r["station_code"], []).append(r)

        print(f"[2/3] 逐站判别 ({len(by_station)} 站) ...")
        all_records = []
        for series in by_station.values():
            all_records.extend(evaluate_station(series))

        print(f"[3/3] upsert 回 biz.biz_disaster_eval ({len(all_records)} 行) ...")
        with conn.cursor() as cur:
            psycopg2.extras.execute_values(cur, UPSERT, all_records, page_size=2000)
        conn.commit()

        # ---- 验收 ----
        print("\n=== 验收: biz_disaster_eval 逐年行数 ===")
        with conn.cursor() as cur:
            cur.execute("SELECT EXTRACT(YEAR FROM obs_date)::int AS yr, COUNT(*) "
                        "FROM biz.biz_disaster_eval GROUP BY yr ORDER BY yr")
            for yr, cnt in cur.fetchall():
                print(f"   {yr}: {cnt}")
            cur.execute("SELECT EXTRACT(YEAR FROM obs_date)::int AS yr, COUNT(*) "
                        "FROM biz.biz_disaster_eval WHERE comp_level >= 1 GROUP BY yr ORDER BY yr")
            print("   -- 其中风险日 (comp_level>=1):")
            for yr, cnt in cur.fetchall():
                print(f"   {yr}: {cnt}")
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n中断")
        sys.exit(130)
