"""
将 data/四川气象站点_YYYY.csv (2020-2023, 逐日, GBK 编码) 接入数据库持久化。

做三件事:
  1. 站点 upsert    -> gis.gis_weather_station  (原始空间数据源)
  2. 日观测入库     -> gis.gis_weather_daily    (幂等, ON CONFLICT 更新)
  3. region_code 回填 + 站点统计字段回填 (A2 / A3)

依赖: psycopg2-binary, pandas
运行前确保:
  init.sql + 02_sichuan_schema.sql + 03_gis_weather_schema.sql + 04_disaster_eval_schema.sql 已执行,
  且已跑过 load_sichuan_boundary.py (gis_admin_region 有区县边界, 否则 A2 回填为空)

用法:
  python load_weather_stations.py                  # 全量 4 年
  python load_weather_stations.py --years 2022,2023
  python load_weather_stations.py --truncate       # 入库前先清空 gis_weather_daily
"""
import argparse
import glob
import os
import sys

import pandas as pd
import psycopg2
import psycopg2.extras

# ---------------- 配置 ----------------
DB_DSN = "host=localhost port=5432 dbname=gcsj user=gcsj password=gcsj123"

# CSV 目录 (脚本在 spatial_analyse/, 数据在 ../data/)
DATA_DIR = os.path.join(os.path.dirname(__file__), "..", "data")
CSV_GLOB = "四川气象站点_*.csv"

# 缺测哨兵: 解码后这些值视为 NULL
MISSING = {"999999", "999990", "999998", "", "nan", "NaN"}

# CSV 列名 (GBK 解码后) -> gis_weather_daily 列
COL_MAP = {
    "平均气温":      "temp_avg",
    "最高气温":      "temp_max",
    "最低气温":      "temp_min",
    "平均相对湿度":  "rh_avg",
    "最小相对湿度":  "rh_min",
    "平均气压":      "pressure_avg",
    "最高本站气压":  "pressure_max",
    "最低本站气压":  "pressure_min",
    "平均2分钟风速": "wind_avg",
    "极大风速":      "wind_max",
    "20-20时降水量": "rainfall",
}
DAILY_COLS = list(COL_MAP.values())


def parse_args():
    p = argparse.ArgumentParser(description="气象站点日观测数据接入")
    p.add_argument("--years", default=None,
                   help="逗号分隔的年份, 如 2022,2023; 缺省则导入全部找到的年份")
    p.add_argument("--truncate", action="store_true",
                   help="入库前清空 gis.gis_weather_daily (全量重建用)")
    return p.parse_args()


def num(v):
    """转 float, 缺测/非法 -> None"""
    if v is None:
        return None
    s = str(v).strip()
    if s in MISSING:
        return None
    try:
        return float(s)
    except ValueError:
        return None


def find_csv_files(years: set[str] | None) -> list[str]:
    files = sorted(glob.glob(os.path.join(DATA_DIR, CSV_GLOB)))
    if not files:
        sys.exit(f"未找到任何 CSV: {os.path.join(DATA_DIR, CSV_GLOB)}")
    if years:
        files = [f for f in files if any(y in os.path.basename(f) for y in years)]
        if not files:
            sys.exit(f"指定年份 {years} 未匹配到 CSV")
    return files


def load_dataframe(files: list[str]) -> pd.DataFrame:
    frames = []
    for f in files:
        print(f"  读取 {os.path.basename(f)} ...")
        df = pd.read_csv(f, encoding="gbk", dtype=str)
        df.columns = [c.strip() for c in df.columns]
        frames.append(df)
    df = pd.concat(frames, ignore_index=True)
    print(f"  合计原始行 {len(df)}")
    return df


def build_obs_date(row) -> str | None:
    """年/月/日 -> 'YYYY-MM-DD', 任一缺失返回 None"""
    try:
        y = int(float(row["年"]))
        m = int(float(row["月"]))
        d = int(float(row["日"]))
        return f"{y:04d}-{m:02d}-{d:02d}"
    except (ValueError, TypeError, KeyError):
        return None


def upsert_stations(cur, df: pd.DataFrame) -> int:
    """按区站号去重, upsert 到 gis_weather_station"""
    # 每个区站号取一条有经纬度的记录作为元数据
    seen: dict[str, tuple] = {}
    for _, r in df.iterrows():
        code = str(r.get("区站号", "")).strip()
        if not code or code in MISSING:
            continue
        lon, lat = num(r.get("经度")), num(r.get("纬度"))
        if lon is None or lat is None:
            continue
        if code in seen:
            continue
        seen[code] = (
            code,
            str(r.get("站名", "")).strip() or code,
            num(r.get("测站高度")),
            lon, lat,
        )
    rows = list(seen.values())
    sql = """
        INSERT INTO gis.gis_weather_station
            (code, name, location, elevation)
        VALUES (%s, %s,
                ST_SetSRID(ST_MakePoint(%s, %s), 4326),
                %s)
        ON CONFLICT (code) DO UPDATE SET
            name      = EXCLUDED.name,
            location  = EXCLUDED.location,
            elevation = EXCLUDED.elevation
    """
    for code, name, elev, lon, lat in rows:
        cur.execute(sql, (code, name, lon, lat, elev))
    return len(rows)


def insert_daily(cur, df: pd.DataFrame) -> int:
    """日观测批量入库, ON CONFLICT (station_code, obs_date) 更新"""
    records = []
    for _, r in df.iterrows():
        code = str(r.get("区站号", "")).strip()
        if not code or code in MISSING:
            continue
        obs_date = build_obs_date(r)
        if obs_date is None:
            continue
        vals = [num(r.get(src)) for src in COL_MAP.keys()]
        records.append((code, obs_date, *vals))

    if not records:
        return 0

    sql = f"""
        INSERT INTO gis.gis_weather_daily
            (station_code, obs_date, {", ".join(DAILY_COLS)})
        VALUES %s
        ON CONFLICT (station_code, obs_date) DO UPDATE SET
            {", ".join(f"{c} = EXCLUDED.{c}" for c in DAILY_COLS)}
    """
    template = "(" + ", ".join(["%s"] * (2 + len(DAILY_COLS))) + ")"
    psycopg2.extras.execute_values(cur, sql, records, template=template, page_size=2000)
    return len(records)


def backfill_region_code(cur) -> int:
    """A2: 按经纬度落在 gis_admin_region 区县(level=3)边界内, 反查 adcode"""
    cur.execute("""
        UPDATE gis.gis_weather_station s
        SET    region_code = r.adcode
        FROM   gis.gis_admin_region r
        WHERE  r.level = 3
          AND  r.boundary IS NOT NULL
          AND  ST_Contains(r.boundary, s.location)
    """)
    return cur.rowcount


def backfill_station_stats(cur) -> int:
    """A3: 回填站点的数据年份覆盖 + 记录数"""
    cur.execute("""
        WITH agg AS (
            SELECT station_code,
                   COUNT(*) AS cnt,
                   string_agg(DISTINCT to_char(obs_date, 'YYYY'), ',' ORDER BY to_char(obs_date, 'YYYY')) AS years
            FROM   gis.gis_weather_daily
            GROUP BY station_code
        )
        UPDATE gis.gis_weather_station s
        SET    record_count  = agg.cnt,
               year_coverage = agg.years
        FROM   agg
        WHERE  s.code = agg.station_code
    """)
    return cur.rowcount


def main():
    args = parse_args()
    years = set(y.strip() for y in args.years.split(",")) if args.years else None

    print("[1/6] 定位 CSV ...")
    files = find_csv_files(years)
    for f in files:
        print(f"   - {os.path.basename(f)}")

    print("[2/6] 读取并合并 CSV ...")
    df = load_dataframe(files)

    print("[3/6] 连接数据库 ...")
    conn = psycopg2.connect(DB_DSN)
    conn.autocommit = False
    try:
        with conn.cursor() as cur:
            if args.truncate:
                print("   --truncate: 清空 gis.gis_weather_daily")
                cur.execute("TRUNCATE gis.gis_weather_daily RESTART IDENTITY")

            print("[4/6] upsert 站点元数据 ...")
            n_st = upsert_stations(cur, df)
            conn.commit()
            print(f"   站点 {n_st} 个")

            print("[5/6] 入库日观测 ...")
            n_obs = insert_daily(cur, df)
            conn.commit()
            print(f"   日观测 {n_obs} 行")

            print("[6/6] 回填 region_code + 站点统计 ...")
            n_region = backfill_region_code(cur)
            n_stats = backfill_station_stats(cur)
            conn.commit()
            print(f"   region_code 命中 {n_region} 站, 统计回填 {n_stats} 站")

            # ---- 验收 ----
            print("\n=== 验收 ===")
            cur.execute("SELECT COUNT(*) FROM gis.gis_weather_station")
            print(f"weather 站点      : {cur.fetchone()[0]}")
            cur.execute("SELECT COUNT(*) FROM gis.gis_weather_daily")
            print(f"日观测记录        : {cur.fetchone()[0]}")
            cur.execute("""
                SELECT MIN(obs_date), MAX(obs_date) FROM gis.gis_weather_daily
            """)
            lo, hi = cur.fetchone()
            print(f"日期范围          : {lo} ~ {hi}")
            cur.execute("""
                SELECT COUNT(*) FROM gis.gis_weather_station
                WHERE region_code IS NOT NULL
            """)
            print(f"已匹配区县的站点  : {cur.fetchone()[0]}")
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
