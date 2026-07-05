package com.gcsj.disaster.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DisasterEvalEngineTest {

    private final DisasterEvalEngine engine = new DisasterEvalEngine();

    private DisasterEvalEngine.DailyInput day(String d, Double rain, Double tmax,
                                               Double tmin, Double rh, Double wind) {
        return new DisasterEvalEngine.DailyInput(
                LocalDate.parse(d), rain, tmax, tmin, rh, wind);
    }

    private DisasterEvalEngine.DailyEval evalSingle(DisasterEvalEngine.DailyInput in) {
        return engine.evaluateStation(List.of(in)).get(0);
    }

    // ---- 暴雨: R >= 25/50/100/250 分四档 ----
    @Test
    void rainstormThresholds() {
        assertEquals(0, evalSingle(day("2021-06-01", 14.9, 20.0, 10.0, 70.0, 0.0)).rainstorm());
        assertEquals(1, evalSingle(day("2021-06-01", 25.0, 20.0, 10.0, 70.0, 0.0)).rainstorm());
        assertEquals(2, evalSingle(day("2021-06-01", 50.0, 20.0, 10.0, 70.0, 0.0)).rainstorm());
        assertEquals(3, evalSingle(day("2021-06-01", 100.0, 20.0, 10.0, 70.0, 0.0)).rainstorm());
        assertEquals(4, evalSingle(day("2021-06-01", 250.0, 20.0, 10.0, 70.0, 0.0)).rainstorm());
    }

    // ---- 高温: Tmax >= 33/35/37/40 分四档 ----
    @Test
    void heatwaveThresholds() {
        assertEquals(0, evalSingle(day("2021-07-01", 0.0, 32.9, 10.0, 70.0, 0.0)).heatwave());
        assertEquals(1, evalSingle(day("2021-07-01", 0.0, 33.0, 10.0, 70.0, 0.0)).heatwave());
        assertEquals(2, evalSingle(day("2021-07-01", 0.0, 35.0, 10.0, 70.0, 0.0)).heatwave());
        assertEquals(3, evalSingle(day("2021-07-01", 0.0, 37.0, 10.0, 70.0, 0.0)).heatwave());
        assertEquals(4, evalSingle(day("2021-07-01", 0.0, 40.0, 10.0, 70.0, 0.0)).heatwave());
    }

    // ---- 寒潮: 24h Tmin 降幅 >= 6/8/10/12 分四档 ----
    @Test
    void coldwaveThresholds() {
        // 24h降温 6°C
        var e = evalTwo(
                day("2021-01-01", 0.0, 10.0, 5.0, 70.0, 0.0),
                day("2021-01-02", 0.0, 10.0, -1.0, 70.0, 0.0));
        assertEquals(1, e.coldwave());
        // 降温 8°C
        e = evalTwo(
                day("2021-01-01", 0.0, 10.0, 7.0, 70.0, 0.0),
                day("2021-01-02", 0.0, 10.0, -1.0, 70.0, 0.0));
        assertEquals(2, e.coldwave());
        // 降温 10°C
        e = evalTwo(
                day("2021-01-01", 0.0, 10.0, 9.0, 70.0, 0.0),
                day("2021-01-02", 0.0, 10.0, -1.0, 70.0, 0.0));
        assertEquals(3, e.coldwave());
        // 降温 12°C
        e = evalTwo(
                day("2021-01-01", 0.0, 10.0, 11.0, 70.0, 0.0),
                day("2021-01-02", 0.0, 10.0, -1.0, 70.0, 0.0));
        assertEquals(4, e.coldwave());
    }

    private DisasterEvalEngine.DailyEval evalTwo(
            DisasterEvalEngine.DailyInput d1,
            DisasterEvalEngine.DailyInput d2) {
        return engine.evaluateStation(List.of(d1, d2)).get(1);
    }

    // ---- 干旱: 月累计降水 + 无雨日占比，无雨日=rainfall<0.1 ----
    @Test
    void droughtDrySeason() {
        // 干季(1月)：整月无雨(rainfall=0) → 无雨日100%, rain=0
        // rain<5 & noRainRatio>0.90 → 红(4)
        List<DisasterEvalEngine.DailyInput> jan = new ArrayList<>();
        for (int d = 1; d <= 31; d++) {
            jan.add(day(String.format("2021-01-%02d", d), 0.0, 10.0, 0.0, 50.0, 0.0));
        }
        var results = engine.evaluateStation(jan);
        for (var r : results) {
            assertEquals(4, r.drought());
        }
    }

    @Test
    void droughtWetSeason() {
        // 湿季(7月)：降雨充足 → 无干旱
        List<DisasterEvalEngine.DailyInput> jul = new ArrayList<>();
        for (int d = 1; d <= 31; d++) {
            jul.add(day(String.format("2021-07-%02d", d), 5.0, 30.0, 20.0, 70.0, 0.0));
        }
        var results = engine.evaluateStation(jul);
        for (var r : results) {
            assertEquals(0, r.drought());
        }
    }

    // ---- 森林火险: Tmax+RH+Wind 三因子联合 ----
    @Test
    void fireRiskRed() {
        assertEquals(4, evalSingle(day("2021-07-01", 0.0, 30.0, 10.0, 15.0, 10.8)).fireRisk());
    }

    @Test
    void fireRiskNone() {
        assertEquals(0, evalSingle(day("2021-07-01", 0.0, 20.0, 10.0, 60.0, 1.0)).fireRisk());
    }

    // ---- 综合等级取五类最高 ----
    @Test
    void compLevelIsMax() {
        var e = evalSingle(day("2021-07-01", 250.0, 40.0, 10.0, 70.0, 0.0));
        assertTrue(e.compLevel() >= 4);
    }

    // ---- 缺测因子 → 该灾种返回 0 ----
    @Test
    void missingValuesYieldZero() {
        var e = evalSingle(day("2021-06-01", null, null, null, null, null));
        assertEquals(0, e.rainstorm());
        assertEquals(0, e.heatwave());
        assertEquals(0, e.coldwave());
        assertEquals(0, e.drought());
        assertEquals(0, e.fireRisk());
        assertEquals(0, e.compLevel());
    }
}
