"""地理定位工具: 地名 -> 经纬度。用 geopy Nominatim。"""
import json

from agents import function_tool

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
        elif key in q or q in key:
            contains.append(c)

    pool = exact or contains
    if not pool:
        return None
    return max(pool, key=_specificity)


def resolve_place(place: str, geocode=None) -> dict:
    """地名 -> 经纬度 (纯函数, geocode 可注入便于测试)。

    geocode: 可调用, 入参 (query, **kw), 返回带 .longitude/.latitude 的对象或 None。
    默认用 geopy Nominatim。
    """
    if geocode is None:
        from geopy.geocoders import Nominatim
        geocode = Nominatim(user_agent="gcsj-disaster-agent", timeout=10).geocode

    try:
        loc = geocode(place)
    except Exception as e:  # 网络/限流等
        return {"found": False, "name": place, "lon": None, "lat": None,
                "message": f"定位服务异常: {e}"}

    if loc is None:
        return {"found": False, "name": place, "lon": None, "lat": None,
                "message": f"未找到地点「{place}」, 请换个更具体的说法 (如加上省市)。"}

    return {"found": True, "name": place,
            "lon": float(loc.longitude), "lat": float(loc.latitude),
            "message": "ok"}


@function_tool
def geo_locate(place: str) -> str:
    """把地名 (景点/城市/区县) 解析为经纬度坐标。

    Args:
        place: 中文地名, 如「九寨沟」「峨眉山」「成都都江堰」。
    Returns:
        JSON 字符串: {found, name, lon, lat, message}
    """
    return json.dumps(resolve_place(place), ensure_ascii=False)
