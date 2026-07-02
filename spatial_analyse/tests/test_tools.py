import json

from spatial_analyse.tools.geo import match_place, resolve_place
from spatial_analyse.tools.gis_query import summarize_disasters
from spatial_analyse.tools.knowledge import lookup_knowledge


class _FakeLoc:
    def __init__(self, lon, lat):
        self.longitude = lon
        self.latitude = lat


def test_resolve_place_found():
    # 传空候选 -> 强制走 geocode 回退; 九寨沟坐标在四川内
    def fake_geocode(q, **kw):
        return _FakeLoc(103.918, 33.262)

    r = resolve_place("九寨沟", geocode=fake_geocode, candidates=[])
    assert r["found"] is True
    assert abs(r["lon"] - 103.918) < 1e-6
    assert abs(r["lat"] - 33.262) < 1e-6
    assert r["name"] == "九寨沟"
    assert r["source"] == "nominatim"
    assert r["in_sichuan"] is True


def test_resolve_place_not_found():
    def fake_geocode(q, **kw):
        return None

    r = resolve_place("不存在的地方xyz", geocode=fake_geocode, candidates=[])
    assert r["found"] is False
    assert r["lon"] is None
    assert "未" in r["message"] or "not" in r["message"].lower()


def test_resolve_place_offline_hit_does_not_call_geocode():
    called = {"n": 0}

    def boom_geocode(q, **kw):
        called["n"] += 1
        raise AssertionError("不该调用 geocode")

    cands = [{"name": "都江堰", "lon": 103.62, "lat": 30.99,
              "source": "region", "level": 3}]
    r = resolve_place("成都都江堰", geocode=boom_geocode, candidates=cands)
    assert r["found"] is True
    assert r["source"] == "region"
    assert r["in_sichuan"] is True
    assert abs(r["lon"] - 103.62) < 1e-6
    assert called["n"] == 0


def test_resolve_place_fallback_outside_sichuan():
    # 回退命中但坐标在上海 (出川) -> found=False 提示补充
    def fake_geocode(q, **kw):
        return _FakeLoc(121.47, 31.23)

    r = resolve_place("某模糊地名", geocode=fake_geocode, candidates=[])
    assert r["found"] is False
    assert r["in_sichuan"] is False
    assert "四川" in r["message"] or "具体" in r["message"]


def test_resolve_place_geocode_exception():
    def bad_geocode(q, **kw):
        raise RuntimeError("限流")

    r = resolve_place("某地", geocode=bad_geocode, candidates=[])
    assert r["found"] is False
    assert "异常" in r["message"] or "限流" in r["message"]


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


def _cands():
    return [
        {"name": "成都", "lon": 104.07, "lat": 30.67, "source": "region", "level": 2},
        {"name": "都江堰", "lon": 103.62, "lat": 30.99, "source": "region", "level": 3},
        {"name": "九寨沟", "lon": 103.92, "lat": 33.26, "source": "station", "level": None},
        {"name": "峨眉山", "lon": 103.48, "lat": 29.60, "source": "station", "level": None},
    ]


def test_match_place_exact():
    r = match_place("成都", _cands())
    assert r["name"] == "成都" and r["source"] == "region"


def test_match_place_station_contains():
    # 站名被包含在 query 中
    r = match_place("我想去峨眉山玩", _cands())
    assert r["name"] == "峨眉山" and r["source"] == "station"


def test_match_place_prefers_more_specific():
    # 同时含 成都(市级) 与 都江堰(县级) -> 取更具体的县/站
    r = match_place("成都都江堰", _cands())
    assert r["name"] == "都江堰"


def test_match_place_no_match():
    assert match_place("上海东方明珠", _cands()) is None


def test_match_place_short_district_no_false_positive():
    cands = [
        {"name": "西区", "lon": 101.72, "lat": 26.58, "source": "region", "level": 3},
        {"name": "东区", "lon": 101.71, "lat": 26.55, "source": "region", "level": 3},
    ]
    # "西岭雪山" 不应因 西区->西 的过度归一化而误命中
    assert match_place("西岭雪山", cands) is None
    # 但用户直接输入 "西区" 仍应精确命中
    assert match_place("西区", cands)["name"] == "西区"
