"""
灾害判别批算 (Python 版, 等价于后端 POST /api/disaster-eval/run)。

读 gis.gis_weather_daily -> 逐站跑判别引擎 -> upsert 回 biz.biz_disaster_eval (幂等)。
判别逻辑逐行复刻 backend DisasterEvalEngine.java (依据 data/算法 .md), 口径与已有 2022 结果一致。

背景: biz_disaster_eval 之前只批算过 2022, 导致历史页/大屏概览按 2020/2021/2023 筛选时为空。
本脚本对缺失年份补算即可。

依赖: psycopg2-binary
用法:
  python run_disaster_eval.py                     # 全部年份 (幂等, 已算过的年份会被覆盖为相同结果)
  python run_disaster_eval.py --years 2020,2021,2023
  python run_disaster_eval.py --station 56571      # 只算单站
"""
import argparse
import sys

import psycopg2
import psycopg2.extras

# ---------------- 配置 ----------------
DB_DSN = "host=localhost port=5432 dbname=gcsj user=gcsj password=gcsj123"

# ---------------- 引擎常量 (对齐 DisasterEvalEngine.java) ----------------
ALPHA = 0.85
WINDOW = 15
# §5.2 AHP 权重
W_R, W_REFF, W_RH, W_V, W_DTR = 0.35, 0.30, 0.15, 0.10, 0.10


import math


def nz(v):
    """None -> 0.0"""
    return 0.0 if v is None else float(v)


def jround(v: float, scale: int) -> float:
    """对齐 Java Math.round (half-up): floor(v*10^s + 0.5)/10^s。
    Python 内建 round() 用银行家舍入 (half-to-even), 会在边界上与 Java 结果差 1 个末位。"""
    f = 10 ** scale
    return math.floor(v * f + 0.5) / f


def clamp(lv: int) -> int:
    return max(0, min(4, lv))


def level(v: float, a: float, b: float, c: float, e: float) -> int:
    """通用四阈值分级: <a ->0, [a,b)->1, [b,c)->2, [c,e)->3, >=e ->4"""
    if v >= e:
        return 4
    if v >= c:
        return 3
    if v >= b:
        return 2
    if v >= a:
        return 1
    return 0


def norm(v: float, full: float) -> float:
    if v <= 0:
        return 0.0
    return min(1.0, v / full)


def effective_rainfall(series, t: int) -> float:
    """§2.2 前期有效降雨量: 回看至多 15 天衰减累加 (不含当日), 缺口日降雨按 0"""
    total = 0.0
    for i in range(1, WINDOW + 1):
        idx = t - i
        if idx < 0:
            break
        total += (ALPHA ** i) * nz(series[idx]["rainfall"])
    return total


def dtr_of(d):
    if d["temp_max"] is None or d["temp_min"] is None:
        return None
    return float(d["temp_max"]) - float(d["temp_min"])


# ---- §4.1 降雨滑坡 ----
def landslide(d, r_eff: float) -> int:
    if d["rainfall"] is None and d["rh_avg"] is None:
        return 0
    by_r = level(nz(d["rainfall"]), 15, 30, 50, 70)
    by_reff = level(r_eff, 60, 100, 150, 200)
    lv = max(by_r, by_reff)
    if lv == 0:
        return 0
    rh = d["rh_avg"]
    if rh is not None:
        if rh >= 85:
            lv += 1
        elif rh < 60:
            lv -= 1
    return lv


# ---- §4.2 降雨泥石流 ----
def mudslide(d, r_eff: float) -> int:
    by_r = level(nz(d["rainfall"]), 20, 40, 60, 90)
    by_reff = level(r_eff, 70, 120, 180, 240)
    lv = max(by_r, by_reff)
    if lv == 0:
        return 0
    v = d["wind_max"]
    if v is not None:
        if v >= 15:
            lv += 2
        elif v >= 10:
            lv += 1
    return lv


# ---- §4.3 冻融滑坡: 仅在发生冻融 (Tmin<0 且 Tmax>0) 时判定 ----
def freezethaw(d, r_eff: float) -> int:
    if d["temp_max"] is None or d["temp_min"] is None:
        return 0
    tmax, tmin = float(d["temp_max"]), float(d["temp_min"])
    if not (tmin < 0 and tmax > 0):
        return 0
    dtr = tmax - tmin
    lv = level(dtr, 10, 15, 20, 25)
    if lv == 0:
        return 0
    if nz(d["rainfall"]) >= 10 or r_eff >= 40:
        lv += 1
    return lv


# ---- §4.4 坡面崩塌 ----
def collapse(d) -> int:
    rh = d["rh_avg"]
    if rh is None:
        return 0
    if rh >= 97:
        lv = 4
    elif rh >= 93:
        lv = 3
    elif rh >= 88:
        lv = 2
    elif rh >= 80:
        lv = 1
    else:
        lv = 0
    if lv == 0:
        return 0
    if nz(d["rainfall"]) >= 25:
        lv += 1
    return lv


# ---- §5 综合风险指数 H ----
def comp_index(d, r_eff: float, dtr) -> float:
    f_r = norm(nz(d["rainfall"]), 70)
    f_reff = norm(r_eff, 200)
    f_rh = 0.0 if d["rh_avg"] is None else norm(float(d["rh_avg"]), 100)
    f_v = 0.0 if d["wind_max"] is None else norm(float(d["wind_max"]), 15)
    f_dtr = 0.0 if dtr is None else norm(dtr, 25)
    return W_R * f_r + W_REFF * f_reff + W_RH * f_rh + W_V * f_v + W_DTR * f_dtr


def evaluate_station(series):
    """series: 按 obs_date 升序的日观测 dict 列表 -> 判别结果 tuple 列表"""
    out = []
    for t, cur in enumerate(series):
        r_eff = effective_rainfall(series, t)
        dtr = dtr_of(cur)
        ls = clamp(landslide(cur, r_eff))
        ms = clamp(mudslide(cur, r_eff))
        ft = clamp(freezethaw(cur, r_eff))
        cp = clamp(collapse(cur))
        comp = max(ls, ms, ft, cp)
        h = comp_index(cur, r_eff, dtr)
        out.append((
            cur["station_code"], cur["obs_date"],
            jround(r_eff, 2), None if dtr is None else jround(dtr, 2),
            ls, ms, ft, cp, comp, jround(h, 3),
        ))
    return out


UPSERT = """
    INSERT INTO biz.biz_disaster_eval
        (station_code, obs_date, r_eff, dtr, landslide_level, mudslide_level,
         freezethaw_level, collapse_level, comp_level, comp_index)
    VALUES %s
    ON CONFLICT (station_code, obs_date) DO UPDATE SET
        r_eff = EXCLUDED.r_eff, dtr = EXCLUDED.dtr,
        landslide_level  = EXCLUDED.landslide_level,
        mudslide_level   = EXCLUDED.mudslide_level,
        freezethaw_level = EXCLUDED.freezethaw_level,
        collapse_level   = EXCLUDED.collapse_level,
        comp_level       = EXCLUDED.comp_level,
        comp_index       = EXCLUDED.comp_index
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
