"""GIS 历史灾害查询工具: 经纬度 -> 就近气象站 -> biz_disaster_eval 历史聚合。"""
import json

import psycopg2
import psycopg2.extras

from agents import function_tool

DB_DSN = "host=localhost port=5432 dbname=gcsj user=gcsj password=gcsj123"

# 四类灾种列 -> 中文名
_HAZARD_COLS = {
    "landslide_level": "滑坡",
    "mudslide_level": "泥石流",
    "freezethaw_level": "冻融滑坡",
    "collapse_level": "坡面崩塌",
}


def summarize_disasters(rows: list) -> dict:
    """把 biz_disaster_eval 行聚合为统计摘要 (纯函数)。

    每行需含: obs_date_month(int 1-12), comp_level(int),
    landslide_level/mudslide_level/freezethaw_level/collapse_level(int)。
    """
    by_level = {1: 0, 2: 0, 3: 0, 4: 0}
    by_month = {m: 0 for m in range(1, 13)}
    hazard_weight = {name: 0 for name in _HAZARD_COLS.values()}
    risk_days = 0
    max_comp = 0

    for r in rows:
        cl = int(r.get("comp_level") or 0)
        max_comp = max(max_comp, cl)
        if cl >= 1:
            risk_days += 1
            if cl in by_level:
                by_level[cl] += 1
            m = int(r.get("obs_date_month") or 0)
            if 1 <= m <= 12:
                by_month[m] += 1
            # 主导灾种: 累加各灾种等级作为权重
            for col, name in _HAZARD_COLS.items():
                hazard_weight[name] += int(r.get(col) or 0)

    dominant = "无"
    if risk_days > 0:
        dominant = max(hazard_weight, key=hazard_weight.get)
        if hazard_weight[dominant] == 0:
            dominant = "无"

    return {
        "total_days": len(rows),
        "risk_days": risk_days,
        "by_level": by_level,
        "by_month": by_month,
        "max_comp_level": max_comp,
        "dominant": dominant,
    }


def nearest_station(lon: float, lat: float, dsn: str = DB_DSN) -> dict:
    """按经纬度就近找站 (PostGIS 距离排序)。返回 {code, name, dist_km} 或 {}。"""
    sql = """
        SELECT code, name,
               ST_Distance(location::geography,
                           ST_SetSRID(ST_MakePoint(%s, %s), 4326)::geography) / 1000.0 AS dist_km
        FROM gis.gis_weather_station
        ORDER BY location <-> ST_SetSRID(ST_MakePoint(%s, %s), 4326)
        LIMIT 1
    """
    conn = psycopg2.connect(dsn)
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(sql, (lon, lat, lon, lat))
            row = cur.fetchone()
    finally:
        conn.close()
    if not row:
        return {}
    return {"code": row["code"], "name": row["name"],
            "dist_km": round(float(row["dist_km"]), 1)}


def _fetch_eval_rows(station_code: str, dsn: str = DB_DSN) -> list:
    sql = """
        SELECT EXTRACT(MONTH FROM obs_date)::int AS obs_date_month,
               comp_level, landslide_level, mudslide_level,
               freezethaw_level, collapse_level
        FROM biz.biz_disaster_eval
        WHERE station_code = %s
    """
    conn = psycopg2.connect(dsn)
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(sql, (station_code,))
            return cur.fetchall()
    finally:
        conn.close()


@function_tool
def query_station_disasters(lon: float, lat: float) -> str:
    """查询给定经纬度就近气象站的历史灾害统计 (2020-2023)。

    Args:
        lon: 经度
        lat: 纬度
    Returns:
        JSON 字符串: {station: {code,name,dist_km}, summary: {...}} 或 {error}
    """
    try:
        st = nearest_station(lon, lat)
        if not st:
            return json.dumps({"error": "附近无气象站数据"}, ensure_ascii=False)
        rows = _fetch_eval_rows(st["code"])
        summary = summarize_disasters(rows)
        return json.dumps({"station": st, "summary": summary}, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"error": f"查询失败: {e}"}, ensure_ascii=False)
