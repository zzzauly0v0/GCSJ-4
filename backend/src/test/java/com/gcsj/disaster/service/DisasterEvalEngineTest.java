package com.gcsj.disaster.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DisasterEvalEngineTest {

    private final DisasterEvalEngine engine = new DisasterEvalEngine();

    /** 单日构造: 只给关心的因子, 其余 null */
    private DisasterEvalEngine.DailyInput day(String d, Double rain, Double tmax, Double tmin, Double rh, Double wind) {
        return new DisasterEvalEngine.DailyInput(LocalDate.parse(d), rain, tmax, tmin, rh, wind);
    }

    private DisasterEvalEngine.DailyEval evalSingle(DisasterEvalEngine.DailyInput in) {
        return engine.evaluateStation(List.of(in)).get(0);
    }

    // ---- §4.1 降雨滑坡: 当日降雨分级 (RH 中性, 无前期降雨) ----
    @Test
    void landslide_rainfall_thresholds() {
        // RH=70 (中性, 不升不降), wind/temp 不影响滑坡
        assertEquals(0, evalSingle(day("2021-06-01", 14.9, 20.0, 10.0, 70.0, 0.0)).landslide());
        assertEquals(1, evalSingle(day("2021-06-01", 15.0, 20.0, 10.0, 70.0, 0.0)).landslide());
        assertEquals(2, evalSingle(day("2021-06-01", 30.0, 20.0, 10.0, 70.0, 0.0)).landslide());
        assertEquals(3, evalSingle(day("2021-06-01", 50.0, 20.0, 10.0, 70.0, 0.0)).landslide());
        assertEquals(4, evalSingle(day("2021-06-01", 70.0, 20.0, 10.0, 70.0, 0.0)).landslide());
    }

    // ---- §4.1.2(3) RH 升降级 ----
    @Test
    void landslide_rh_adjust() {
        // 基础黄(2): R=30, RH>=85 升一级 -> 橙(3)
        assertEquals(3, evalSingle(day("2021-06-01", 30.0, 20.0, 10.0, 85.0, 0.0)).landslide());
        // 基础黄(2): RH<60 降一级 -> 蓝(1)
        assertEquals(1, evalSingle(day("2021-06-01", 30.0, 20.0, 10.0, 59.0, 0.0)).landslide());
    }

    // ---- §4.2 泥石流: 风速增强 ----
    @Test
    void mudslide_wind_boost() {
        // R=20 -> 蓝(1); V>=10 +1 -> 黄(2)
        assertEquals(2, evalSingle(day("2021-06-01", 20.0, 20.0, 10.0, 70.0, 10.0)).mudslide());
        // R=20 -> 蓝(1); V>=15 +2 -> 橙(3)
        assertEquals(3, evalSingle(day("2021-06-01", 20.0, 20.0, 10.0, 70.0, 15.0)).mudslide());
    }

    // ---- §4.3 冻融: 仅在 Tmin<0 且 Tmax>0 才判定 ----
    @Test
    void freezethaw_requires_cycle() {
        // 无冻融 (Tmin>=0) -> 0
        assertEquals(0, evalSingle(day("2021-01-01", 30.0, 5.0, 0.0, 70.0, 0.0)).freezethaw());
        // 发生冻融 Tmin=-5 Tmax=12 DTR=17 -> 黄(2)
        assertEquals(2, evalSingle(day("2021-01-01", 0.0, 12.0, -5.0, 70.0, 0.0)).freezethaw());
        // 同上叠加 R>=10 升一级 -> 橙(3)
        assertEquals(3, evalSingle(day("2021-01-01", 10.0, 12.0, -5.0, 70.0, 0.0)).freezethaw());
    }

    // ---- §4.4 崩塌: RH 分级 + 降雨 +1 ----
    @Test
    void collapse_rh_and_rain() {
        assertEquals(1, evalSingle(day("2021-06-01", 0.0, 20.0, 10.0, 80.0, 0.0)).collapse());
        assertEquals(2, evalSingle(day("2021-06-01", 0.0, 20.0, 10.0, 88.0, 0.0)).collapse());
        // RH=88 -> 黄(2); R>=25 +1 -> 橙(3)
        assertEquals(3, evalSingle(day("2021-06-01", 25.0, 20.0, 10.0, 88.0, 0.0)).collapse());
    }

    // ---- 综合等级取四类最高 (§6) ----
    @Test
    void comp_level_is_max() {
        // R=70 滑坡红(4), 其余更低 -> comp=4
        var e = evalSingle(day("2021-06-01", 70.0, 20.0, 10.0, 70.0, 0.0));
        assertEquals(4, e.compLevel());
    }

    // ---- R_eff: 前一日降雨衰减累加 α=0.85 ----
    @Test
    void r_eff_decay_accumulation() {
        // day1 R=100, day2 R=0 -> day2 的 r_eff = 0.85^1 * 100 = 85.0
        var list = engine.evaluateStation(List.of(
                day("2021-06-01", 100.0, 20.0, 10.0, 70.0, 0.0),
                day("2021-06-02", 0.0, 20.0, 10.0, 70.0, 0.0)));
        assertEquals(85.0, list.get(1).rEff(), 0.01);
    }

    // ---- 缺测: 关键因子 null -> 该灾种 0, 不抛异常 ----
    @Test
    void missing_values_yield_zero() {
        var e = evalSingle(day("2021-06-01", null, null, null, null, null));
        assertEquals(0, e.landslide());
        assertEquals(0, e.mudslide());
        assertEquals(0, e.freezethaw());
        assertEquals(0, e.collapse());
        assertEquals(0, e.compLevel());
    }
}
