"""
从阿里 DataV 拉取四川省 + 21 市州 + 全部区县边界, 写入 gis.gis_admin_region。

阿里 DataV 边界 API (公开, 无需 key):
  https://geo.datav.aliyun.com/areas_v3/bound/{adcode}_full.json
    -> 返回 GeoJSON FeatureCollection, 包含该 adcode 下所有下级行政区
  https://geo.datav.aliyun.com/areas_v3/bound/{adcode}.json
    -> 返回该 adcode 自身的边界 (不含下级)

四川省 adcode = 510000

依赖: psycopg2-binary, requests, shapely
运行前确保: 已执行 init.sql + 02_sichuan_schema.sql
"""
import json
import sys
from typing import Iterable

import psycopg2
import psycopg2.extras
import requests
from shapely.geometry import shape

# ---------------- 配置 ----------------
DB_DSN = "host=localhost port=5432 dbname=gcsj user=gcsj password=gcsj123"
SICHUAN_ADCODE = "510000"
DATAV_FULL = "https://geo.datav.aliyun.com/areas_v3/bound/{adcode}_full.json"
DATAV_SELF = "https://geo.datav.aliyun.com/areas_v3/bound/{adcode}.json"

session = requests.Session()
session.headers.update({"User-Agent": "gcsj-loader/1.0"})


def fetch(url: str) -> dict:
    print(f"  GET {url}")
    r = session.get(url, timeout=30)
    r.raise_for_status()
    return r.json()


def to_multipolygon_wkt(geom_geojson: dict) -> str:
    """阿里返回可能是 Polygon 或 MultiPolygon, 统一转 MultiPolygon WKT。"""
    geom = shape(geom_geojson)
    if geom.geom_type == "Polygon":
        from shapely.geometry import MultiPolygon
        geom = MultiPolygon([geom])
    elif geom.geom_type != "MultiPolygon":
        raise ValueError(f"unsupported geometry: {geom.geom_type}")
    return geom.wkt


def upsert_rows(conn, rows: Iterable[tuple]) -> int:
    """rows: (adcode, name, level, parent_code, center_wkt, boundary_wkt)"""
    sql = """
        INSERT INTO gis.gis_admin_region
            (adcode, name, level, parent_code, center, boundary, area_km2)
        VALUES (%s, %s, %s, %s,
                ST_GeomFromText(%s, 4326),
                ST_Multi(ST_GeomFromText(%s, 4326)),
                ROUND( (ST_Area(ST_GeomFromText(%s, 4326)::geography) / 1e6)::numeric, 2 ))
        ON CONFLICT (adcode) DO UPDATE SET
            name        = EXCLUDED.name,
            level       = EXCLUDED.level,
            parent_code = EXCLUDED.parent_code,
            center      = EXCLUDED.center,
            boundary    = EXCLUDED.boundary,
            area_km2    = EXCLUDED.area_km2
    """
    n = 0
    with conn.cursor() as cur:
        for adcode, name, level, parent, center_wkt, boundary_wkt in rows:
            cur.execute(sql, (adcode, name, level, parent,
                              center_wkt, boundary_wkt, boundary_wkt))
            n += 1
    return n


def parse_features(data: dict, expected_level: int, parent_code: str | None):
    """阿里 _full.json 的 features 里 properties 包含 adcode/name/level/center"""
    for feat in data.get("features", []):
        props = feat.get("properties", {})
        adcode = str(props.get("adcode"))
        name = props.get("name")
        center = props.get("center")  # [lon, lat]
        geom = feat.get("geometry")
        if not adcode or not name or not geom:
            continue
        if center and isinstance(center, (list, tuple)) and len(center) == 2:
            center_wkt = f"POINT({center[0]} {center[1]})"
        else:
            center_wkt = None
        boundary_wkt = to_multipolygon_wkt(geom)
        yield (adcode, name, expected_level, parent_code, center_wkt, boundary_wkt)


def main():
    print("[1/4] 连接数据库 ...")
    conn = psycopg2.connect(DB_DSN)
    conn.autocommit = False

    try:
        # ---- 省自身 (level=1) ----
        print(f"[2/4] 拉取四川省自身边界 (adcode={SICHUAN_ADCODE}) ...")
        sc_self = fetch(DATAV_SELF.format(adcode=SICHUAN_ADCODE))
        # _self 接口直接返回单 Feature 或 FeatureCollection, 兼容处理
        if sc_self.get("type") == "Feature":
            sc_self = {"type": "FeatureCollection", "features": [sc_self]}
        province_rows = list(parse_features(sc_self, expected_level=1, parent_code=None))
        n = upsert_rows(conn, province_rows)
        print(f"   写入省级: {n} 条")

        # ---- 21 市州 (level=2) ----
        print(f"[3/4] 拉取四川下属 21 市州 ...")
        sc_full = fetch(DATAV_FULL.format(adcode=SICHUAN_ADCODE))
        city_rows = list(parse_features(sc_full, expected_level=2, parent_code=SICHUAN_ADCODE))
        n = upsert_rows(conn, city_rows)
        print(f"   写入市州: {n} 条")

        # ---- 各市下区县 (level=3) ----
        print(f"[4/4] 拉取各市州下属区县 ...")
        total = 0
        cities = [(r[0], r[1]) for r in city_rows]
        for city_code, city_name in cities:
            try:
                city_full = fetch(DATAV_FULL.format(adcode=city_code))
            except requests.HTTPError as e:
                print(f"   ! {city_name}({city_code}) 接口失败: {e}, 跳过")
                continue
            county_rows = list(parse_features(city_full, expected_level=3, parent_code=city_code))
            if county_rows:
                upsert_rows(conn, county_rows)
                total += len(county_rows)
                print(f"   {city_name}({city_code}): {len(county_rows)} 个区县")

        conn.commit()
        print(f"\n完成: 区县共 {total} 条")

        # ---- 回填灾害事件 region_code ----
        print("\n回填 biz_disaster_event.region_code ...")
        with conn.cursor() as cur:
            cur.execute("""
                UPDATE biz.biz_disaster_event e
                SET    region_code = r.adcode
                FROM   gis.gis_admin_region r
                WHERE  r.level = 3
                  AND  ST_Within(e.location, r.boundary)
            """)
            print(f"   更新 {cur.rowcount} 条灾害事件")
        conn.commit()

        # ---- 验收 ----
        with conn.cursor() as cur:
            cur.execute("""
                SELECT level, COUNT(*) FROM gis.gis_admin_region
                GROUP BY level ORDER BY level
            """)
            print("\n行政区入库统计:")
            for lvl, cnt in cur.fetchall():
                tag = {1: "省", 2: "市/州", 3: "区/县"}.get(lvl, f"L{lvl}")
                print(f"  {tag}: {cnt}")

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
