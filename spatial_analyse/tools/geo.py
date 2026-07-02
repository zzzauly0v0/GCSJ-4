"""地理定位工具: 地名 -> 经纬度。用 geopy Nominatim。"""
import json

from agents import function_tool


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
