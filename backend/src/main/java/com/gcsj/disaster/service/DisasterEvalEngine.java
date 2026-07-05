package com.gcsj.disaster.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Month;
import java.util.*;

/**
 * 气象灾害判别引擎：暴雨、高温热浪、寒潮、干旱、森林火险五类。
 * 依据国标/行标：
 *  - 暴雨: GB/T 28592-2012
 *  - 高温热浪: GB/T 20481-2017
 *  - 寒潮: GB/T 21987-2017 (24h降温幅度)
 *  - 干旱: GB/T 20481-2017 (月累计降水 + 无雨日占比，干/湿季分档)
 *  - 森林火险: LY/T 1172-95 + QX/T 77-2007
 *
 * 约定:
 *  - 等级编码 0 无 / 1 蓝 / 2 黄 / 3 橙 / 4 红
 *  - 缺测因子 (null): 该灾种记 0 (无法判别即无风险)
 *  - 综合等级 comp = max(五类), 综合指数 compIndex = comp / 4.0
 *  - 干旱特殊处理: 同月所有天等级一致，需预计算月累计降水 + 无雨日占比
 */
@Component
public class DisasterEvalEngine {

    /** 干季月份 (11-4月) */
    private static final Set<Integer> DRY_MONTHS = Set.of(11, 12, 1, 2, 3, 4);

    public record DailyInput(LocalDate date, Double rainfall, Double tempMax,
                             Double tempMin, Double rhAvg, Double windMax) {}

    public record DailyEval(LocalDate date,
                            int rainstorm, int heatwave, int coldwave,
                            int drought, int fireRisk,
                            int compLevel, double compIndex) {}

    // ---- 月份级干旱预计算缓存 ----
    private record MonthKey(int year, int month) {}
    private record MonthStats(double totalRain, double noRainRatio) {}

    public List<DailyEval> evaluateStation(List<DailyInput> seriesAsc) {
        // 第一遍：预计算各月累计降水与无雨日占比
        Map<MonthKey, MonthStats> monthCache = precomputeMonths(seriesAsc);

        List<DailyEval> out = new ArrayList<>(seriesAsc.size());
        for (int t = 0; t < seriesAsc.size(); t++) {
            DailyInput cur = seriesAsc.get(t);
            DailyInput prev = t > 0 ? seriesAsc.get(t - 1) : null;

            int rs = rainstorm(cur);
            int hw = heatwave(cur);
            int cw = coldwave(cur, prev);
            int dr = drought(cur, monthCache);
            int fr = fireRisk(cur);

            int comp = max5(rs, hw, cw, dr, fr);
            double compIndex = comp / 4.0;

            out.add(new DailyEval(cur.date(), rs, hw, cw, dr, fr, comp, round3(compIndex)));
        }
        return out;
    }

    // ==================== 干旱预计算 ====================
    private Map<MonthKey, MonthStats> precomputeMonths(List<DailyInput> series) {
        Map<MonthKey, List<DailyInput>> byMonth = new LinkedHashMap<>();
        for (DailyInput d : series) {
            MonthKey k = new MonthKey(d.date().getYear(), d.date().getMonthValue());
            byMonth.computeIfAbsent(k, x -> new ArrayList<>()).add(d);
        }
        Map<MonthKey, MonthStats> cache = new LinkedHashMap<>();
        for (Map.Entry<MonthKey, List<DailyInput>> e : byMonth.entrySet()) {
            List<DailyInput> days = e.getValue();
            double totalRain = 0;
            int noRainDays = 0;
            for (DailyInput d : days) {
                double r = nz(d.rainfall());
                totalRain += r;
                if (r < 0.1) noRainDays++;
            }
            double ratio = days.isEmpty() ? 0 : (double) noRainDays / days.size();
            cache.put(e.getKey(), new MonthStats(totalRain, ratio));
        }
        return cache;
    }

    // ==================== 五类判别 ====================

    /** 暴雨: GB/T 28592-2012, 日降水量四阈值 */
    private int rainstorm(DailyInput d) {
        if (d.rainfall() == null) return 0;
        double r = d.rainfall();
        return level(r, 25, 50, 100, 250);
    }

    /** 高温热浪: GB/T 20481-2017, 日最高气温四阈值 */
    private int heatwave(DailyInput d) {
        if (d.tempMax() == null) return 0;
        double t = d.tempMax();
        return level(t, 33, 35, 37, 40);
    }

    /** 寒潮: GB/T 21987-2017, 24h最低气温降幅 */
    private int coldwave(DailyInput cur, DailyInput prev) {
        if (cur.tempMin() == null || prev == null || prev.tempMin() == null) return 0;
        double drop = prev.tempMin() - cur.tempMin(); // 正数=降温
        if (drop < 6) return 0;
        return level(drop, 6, 8, 10, 12);
    }

    /** 干旱: GB/T 20481-2017，月累计降水 + 无雨日占比，干/湿季分档 */
    private int drought(DailyInput d, Map<MonthKey, MonthStats> cache) {
        MonthKey k = new MonthKey(d.date().getYear(), d.date().getMonthValue());
        MonthStats s = cache.get(k);
        if (s == null) return 0;
        double rain = s.totalRain();
        double noRainRatio = s.noRainRatio();
        boolean isDry = DRY_MONTHS.contains(d.date().getMonthValue());

        if (isDry) {
            // 干季 (11-4月)
            if (rain < 5 && noRainRatio > 0.90) return 4;
            if (rain < 15 && noRainRatio > 0.80) return 3;
            if (rain < 30 && noRainRatio > 0.70) return 2;
            if (rain < 50 && noRainRatio > 0.60) return 1;
        } else {
            // 湿季 (5-10月)
            if (rain < 20 && noRainRatio > 0.90) return 4;
            if (rain < 50 && noRainRatio > 0.80) return 3;
            if (rain < 80 && noRainRatio > 0.70) return 2;
            if (rain < 120 && noRainRatio > 0.60) return 1;
        }
        return 0;
    }

    /** 森林火险: LY/T 1172-95 + QX/T 77-2007 */
    private int fireRisk(DailyInput d) {
        double t = nz(d.tempMax()), rh = nz(d.rhAvg()), w = nz(d.windMax());

        if (t >= 30 && rh <= 15 && w >= 10.8) return 4;
        if (t >= 28 && rh <= 20 && w >= 8.0)  return 3;
        if (t >= 26 && rh <= 25 && w >= 6.0)  return 2;
        if (t >= 24 && rh <= 30 && w >= 4.0)  return 1;
        return 0;
    }

    // ==================== 工具方法 ====================
    private int max5(int a, int b, int c, int d, int e) {
        return Math.max(Math.max(Math.max(a, b), Math.max(c, d)), e);
    }

    private int level(double v, double a, double b, double c, double e) {
        if (v >= e) return 4;
        if (v >= c) return 3;
        if (v >= b) return 2;
        if (v >= a) return 1;
        return 0;
    }

    private double nz(Double v) { return v == null ? 0.0 : v; }
    private double round3(double v) { return Math.round(v * 1000.0) / 1000.0; }
}
