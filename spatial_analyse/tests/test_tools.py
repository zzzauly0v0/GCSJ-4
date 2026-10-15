import json

import pytest

from spatial_analyse.tools.geo import match_place, resolve_place
from spatial_analyse.tools.gis_query import (
    summarize_disasters, nearest_station, _fetch_eval_rows,
)
from spatial_analyse.tools.knowledge import lookup_knowledge

# 真实测试锚点: 数据库中确实存在、且四类灾种(滑坡/暴雨/高温/干旱)均有判别数据的站。
# 金阳站(凉山州), biz_disaster_eval 覆盖 2020-2023 共 1461 天, 综合风险日 1461。
# 站号/坐标经 `SELECT ... FROM biz.biz_disaster_eval JOIN gis.gis_weather_station` 核对。
ANCHOR_STATION_CODE = "56584"
ANCHOR_STATION_NAME = "金阳"
ANCHOR_LON, ANCHOR_LAT = 103.25, 27.7


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
        # obs_date, comp_level, landslide(滑坡), mudslide(暴雨), freezethaw(高温), collapse(干旱)
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
    # 各灾种独立达标天数: 滑坡 2 天, 暴雨 2 天, 干旱 1 天, 高温 0 天
    assert s["by_hazard"]["滑坡"] == 2
    assert s["by_hazard"]["暴雨"] == 2
    assert s["by_hazard"]["干旱"] == 1
    assert s["by_hazard"]["高温热浪"] == 0
    assert s["hazard_max_level"]["滑坡"] == 3
    assert s["hazard_max_level"]["暴雨"] == 4
    # 主导取达标天数最多者: 滑坡 2 vs 暴雨 2 (并列, max 取先出现者)
    assert s["dominant"] in ("滑坡", "暴雨")


def test_summarize_disasters_empty():
    s = summarize_disasters([])
    assert s["total_days"] == 0
    assert s["risk_days"] == 0
    assert s["max_comp_level"] == 0
    assert s["dominant"] == "无"


def test_lookup_knowledge_matches_hazard():
    text = lookup_knowledge("高温热浪")
    assert "高温" in text
    assert len(text) > 20


def test_lookup_knowledge_fallback():
    text = lookup_knowledge("完全不相关的词")
    # 无匹配 -> 返回总览, 应覆盖全部四类灾种名
    assert all(h in text for h in ("滑坡", "暴雨", "高温", "干旱"))


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


# ============================================================================
# 连库集成测试: 校验 agent 查询确实从数据库取数 (无数据库则跳过, 不阻塞纯函数单测)。
# 需 PostgreSQL 就绪并已跑过 merged_disaster_eval.py 回填 biz_disaster_eval。
# ============================================================================

def _db_available():
    try:
        import psycopg2
        from spatial_analyse.tools.db import DB_DSN
        conn = psycopg2.connect(DB_DSN)
        conn.close()
        return True
    except Exception:
        return False


requires_db = pytest.mark.skipif(not _db_available(), reason="数据库不可用, 跳过连库集成测试")


@requires_db
def test_nearest_station_hits_anchor():
    # 用锚点站坐标反查, 应就近命中该站自身
    st = nearest_station(ANCHOR_LON, ANCHOR_LAT)
    assert st, "附近应能查到气象站"
    assert st["code"] == ANCHOR_STATION_CODE
    assert st["name"] == ANCHOR_STATION_NAME
    assert st["dist_km"] < 5.0            # 用站点自身坐标查, 距离应≈0


@requires_db
def test_fetch_eval_rows_returns_real_data():
    rows = _fetch_eval_rows(ANCHOR_STATION_CODE)
    assert len(rows) > 1000, "金阳站应有 2020-2023 逐日判别数据"
    keys = set(rows[0].keys())
    assert {"obs_date_month", "comp_level", "landslide_level",
            "mudslide_level", "freezethaw_level", "collapse_level"} <= keys
    assert all(1 <= r["obs_date_month"] <= 12 for r in rows)


@requires_db
def test_summarize_real_station_four_hazards():
    # 端到端: 就近站 -> 取判别行 -> 聚合, 验证四类灾种都被统计到
    st = nearest_station(ANCHOR_LON, ANCHOR_LAT)
    s = summarize_disasters(_fetch_eval_rows(st["code"]))
    assert s["total_days"] > 1000
    assert set(s["by_hazard"]) == {"滑坡", "暴雨", "高温热浪", "干旱"}
    # 金阳站四类灾种历史上均有达标记录
    assert all(s["by_hazard"][h] > 0 for h in ("滑坡", "暴雨", "高温热浪", "干旱"))
    assert s["dominant"] in ("滑坡", "暴雨", "高温热浪", "干旱")
    assert 1 <= s["max_comp_level"] <= 4
