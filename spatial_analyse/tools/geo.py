"""地理定位工具: 地名 -> 经纬度。用 geopy Nominatim。"""
import json

from agents import function_tool

from spatial_analyse.tools.db import DB_DSN, connect

# 行政区划名归一化: 去掉这些后缀后再比较
_REGION_SUFFIXES = ("自治州", "省", "市", "州", "县", "区")


def _norm_region(name: str) -> str:
    """行政区划名去后缀 (自治州优先, 避免只去掉'州')。"""
    for suf in _REGION_SUFFIXES:
        if name.endswith(suf) and len(name) > len(suf):
            return name[: -len(suf)]
    return name


def _specificity(cand: dict) -> int:
    """平局时取更具体者: 站点最具体, 其次行政级别数字越大越具体。"""
    if cand["source"] == "station":
        return 100
    return int(cand.get("level") or 0)


def match_place(place: str, candidates: list) -> dict:
    """把地名匹配到库内站点/区县候选, 命中返回该 candidate, 否则 None (纯函数)。

    优先级: 精确等名 > 包含匹配; 同优先级下取更具体者 (站点 > 县 > 市 > 省)。
    """
    q = (place or "").strip()
    if not q:
        return None

    exact, contains = [], []
    for c in candidates:
        raw = c["name"].strip()
        key = _norm_region(raw) if c["source"] == "region" else raw
        if not key:
            continue
        if q == raw or q == key:
            exact.append(c)
        elif len(key) >= 2 and (key in q or q in key):
            # 归一化后 key 过短 (如 西区->西) 时跳过子串匹配, 避免误命中任何含该字的查询
            contains.append(c)

    pool = exact or contains
    if not pool:
        return None
    return max(pool, key=_specificity)


def load_place_candidates(dsn: str = DB_DSN) -> list:
    """从库里加载站点名 + 行政区划名候选 (含经纬度)。

    station 用 location 点, region 用 center 点。center 为空的区划跳过。
    """
    sql = """
        SELECT name,
               ST_X(location) AS lon, ST_Y(location) AS lat,
               'station' AS source, NULL::int AS level
        FROM gis.gis_weather_station
        WHERE location IS NOT NULL
        UNION ALL
        SELECT name,
               ST_X(center) AS lon, ST_Y(center) AS lat,
               'region' AS source, level
        FROM gis.gis_admin_region
        WHERE center IS NOT NULL
    """
    import psycopg2.extras
    conn = connect(dsn)
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(sql)
            rows = cur.fetchall()
    finally:
        conn.close()
    return [
        {"name": r["name"], "lon": float(r["lon"]), "lat": float(r["lat"]),
         "source": r["source"], "level": r["level"]}
        for r in rows
    ]


# 四川 bbox (宽松包络): 用于校验回退坐标是否在川
_SC_LON = (97.3, 108.6)
_SC_LAT = (26.0, 34.4)


def _in_sichuan(lon: float, lat: float) -> bool:
    return _SC_LON[0] <= lon <= _SC_LON[1] and _SC_LAT[0] <= lat <= _SC_LAT[1]


def resolve_place(place: str, geocode=None, candidates=None) -> dict:
    """地名 -> 经纬度: 先库内站点/区划离线匹配, 未命中回退 Nominatim 并校验在川。

    candidates: None 则从库加载; 传 list (含空 list) 则直接用, 不查库 (便于测试)。
    geocode: 可调用, 入参 (query, **kw), 返回带 .longitude/.latitude 的对象或 None。
    """
    # 1) 离线匹配库内候选
    if candidates is None:
        try:
            candidates = load_place_candidates()
        except Exception:
            candidates = []
    hit = match_place(place, candidates)
    if hit is not None:
        return {"found": True, "name": place,
                "lon": float(hit["lon"]), "lat": float(hit["lat"]),
                "source": hit["source"], "in_sichuan": True, "message": "ok"}

    # 2) 回退 Nominatim
    if geocode is None:
        from geopy.geocoders import Nominatim
        geocode = Nominatim(user_agent="gcsj-disaster-agent", timeout=10).geocode

    try:
        loc = geocode(place)
    except Exception as e:  # 网络/限流等
        return {"found": False, "name": place, "lon": None, "lat": None,
                "source": None, "in_sichuan": False,
                "message": f"定位服务异常: {e}"}

    if loc is None:
        return {"found": False, "name": place, "lon": None, "lat": None,
                "source": None, "in_sichuan": False,
                "message": f"未找到地点「{place}」, 请换个更具体的说法 (如加上省市)。"}

    lon, lat = float(loc.longitude), float(loc.latitude)
    if not _in_sichuan(lon, lat):
        return {"found": False, "name": place, "lon": None, "lat": None,
                "source": None, "in_sichuan": False,
                "message": f"地点「{place}」似乎不在四川范围内, 请补充更具体的四川地名。"}

    return {"found": True, "name": place, "lon": lon, "lat": lat,
            "source": "nominatim", "in_sichuan": True, "message": "ok"}


@function_tool
def geo_locate(place: str) -> str:
    """把地名 (景点/城市/区县) 解析为经纬度坐标。

    Args:
        place: 中文地名, 如「九寨沟」「峨眉山」「成都都江堰」。
    Returns:
        JSON 字符串: {found, name, lon, lat, source, in_sichuan, message}
    """
    return json.dumps(resolve_place(place), ensure_ascii=False)
