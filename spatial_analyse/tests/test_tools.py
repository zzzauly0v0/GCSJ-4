import json

from spatial_analyse.tools.geo import resolve_place
from spatial_analyse.tools.gis_query import summarize_disasters
from spatial_analyse.tools.knowledge import lookup_knowledge


class _FakeLoc:
    def __init__(self, lon, lat):
        self.longitude = lon
        self.latitude = lat


def test_resolve_place_found():
    # 注入假 geocoder: 返回九寨沟坐标
    def fake_geocode(q, **kw):
        return _FakeLoc(103.918, 33.262)

    r = resolve_place("九寨沟", geocode=fake_geocode)
    assert r["found"] is True
    assert abs(r["lon"] - 103.918) < 1e-6
    assert abs(r["lat"] - 33.262) < 1e-6
    assert r["name"] == "九寨沟"


def test_resolve_place_not_found():
    def fake_geocode(q, **kw):
        return None

    r = resolve_place("不存在的地方xyz", geocode=fake_geocode)
    assert r["found"] is False
    assert r["lon"] is None
    assert "未" in r["message"] or "not" in r["message"].lower()


def test_summarize_disasters_counts_levels_and_months():
    rows = [
        # obs_date, comp_level, landslide, mudslide, freezethaw, collapse
        {"obs_date_month": 9, "comp_level": 4, "landslide_level": 2,
         "mudslide_level": 4, "freezethaw_level": 0, "collapse_level": 1},
        {"obs_date_month": 9, "comp_level": 3, "landslide_level": 3,
         "mudslide_level": 1, "freezethaw_level": 0, "collapse_level": 0},
        {"obs_date_month": 1, "comp_level": 0, "landslide_level": 0,
         "mudslide_level": 0, "freezethaw_level": 0, "collapse_level": 0},
    ]
    s = summarize_disasters(rows)
    assert s["total_days"] == 3
    assert s["risk_days"] == 2                 # comp_level>=1 的天数
    assert s["by_level"][4] == 1
    assert s["by_level"][3] == 1
    assert s["by_month"][9] == 2               # 9 月 2 个风险日
    assert s["by_month"][1] == 0               # 1 月无风险日
    assert s["max_comp_level"] == 4
    # 泥石流(4)+滑坡(3) 各出现, 主导取达标(>=1)次数最多: 滑坡2次 vs 泥石流2次 -> 取风险总和更高者
    assert s["dominant"] in ("滑坡", "泥石流")


def test_summarize_disasters_empty():
    s = summarize_disasters([])
    assert s["total_days"] == 0
    assert s["risk_days"] == 0
    assert s["max_comp_level"] == 0
    assert s["dominant"] == "无"


def test_lookup_knowledge_matches_hazard():
    text = lookup_knowledge("泥石流")
    assert "泥石流" in text
    assert len(text) > 20


def test_lookup_knowledge_fallback():
    text = lookup_knowledge("完全不相关的词")
    # 无匹配 -> 返回总览, 至少包含多个灾种名
    assert "暴雨" in text and "滑坡" in text
