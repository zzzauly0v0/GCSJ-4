package com.gcsj.disaster.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 气象地质灾害判别引擎 (依据 data/算法 .md)。
 * 纯逻辑、无 Spring/DB 依赖，可独立单测。
 *
 * 约定:
 *  - 等级编码 0 无 / 1 蓝 / 2 黄 / 3 橙 / 4 红, 结果裁剪到 [0,4]
 *  - 缺测因子 (null): 降雨按 0 参与 R_eff; 判定某灾种所需因子为 null 时该灾种记 0 (无法判别即无风险)
 *  - R_eff = Σ(i=1..15) α^i · R_{t-i}, α=0.85 (§2.2), 仅在同站序列内回看, 缺口日降雨按 0
 */
@Component
public class DisasterEvalEngine {

    private static final double ALPHA = 0.85;
    private static final int    WINDOW = 15;

    // §5.2 AHP 权重
    private static final double W_R = 0.35, W_REFF = 0.30, W_RH = 0.15, W_V = 0.10, W_DTR = 0.10;

    public record DailyInput(LocalDate date, Double rainfall, Double tempMax,
                             Double tempMin, Double rhAvg, Double windMax) {}

    public record DailyEval(LocalDate date, double rEff, Double dtr,
                            int landslide, int mudslide, int freezethaw, int collapse,
                            int compLevel, double compIndex) {}

    public List<DailyEval> evaluateStation(List<DailyInput> seriesAsc) {
        List<DailyEval> out = new ArrayList<>(seriesAsc.size());
        for (int t = 0; t < seriesAsc.size(); t++) {
            DailyInput cur = seriesAsc.get(t);
            double rEff = effectiveRainfall(seriesAsc, t);
            Double dtr = dtr(cur);

            int ls = clamp(landslide(cur, rEff));
            int ms = clamp(mudslide(cur, rEff));
            int ft = clamp(freezethaw(cur, rEff));
            int cp = clamp(collapse(cur));
            int comp = Math.max(Math.max(ls, ms), Math.max(ft, cp));
            double h = compIndex(cur, rEff, dtr);

            out.add(new DailyEval(cur.date(), round2(rEff), dtr == null ? null : round2(dtr),
                    ls, ms, ft, cp, comp, round3(h)));
        }
        return out;
    }

    /** §2.2 前期有效降雨量: 回看至多 15 天, 衰减累加 (不含当日) */
    private double effectiveRainfall(List<DailyInput> s, int t) {
        double sum = 0;
        for (int i = 1; i <= WINDOW; i++) {
            int idx = t - i;
            if (idx < 0) break;
            double r = nz(s.get(idx).rainfall());
            sum += Math.pow(ALPHA, i) * r;
        }
        return sum;
    }

    // ---- §4.1 降雨滑坡 ----
    private int landslide(DailyInput d, double rEff) {
        if (d.rainfall() == null && d.rhAvg() == null) return 0;
        int byR = level(nz(d.rainfall()), 15, 30, 50, 70);
        int byReff = level(rEff, 60, 100, 150, 200);
        int lv = Math.max(byR, byReff);
        if (lv == 0) return 0;
        Double rh = d.rhAvg();
        if (rh != null) {
            if (rh >= 85) lv += 1;
            else if (rh < 60) lv -= 1;
        }
        return lv;
    }

    // ---- §4.2 降雨泥石流 ----
    private int mudslide(DailyInput d, double rEff) {
        int byR = level(nz(d.rainfall()), 20, 40, 60, 90);
        int byReff = level(rEff, 70, 120, 180, 240);
        int lv = Math.max(byR, byReff);
        if (lv == 0) return 0;
        Double v = d.windMax();
        if (v != null) {
            if (v >= 15) lv += 2;
            else if (v >= 10) lv += 1;
        }
        return lv;
    }

    // ---- §4.3 冻融滑坡: 仅在发生冻融 (Tmin<0 且 Tmax>0) 时判定 ----
    private int freezethaw(DailyInput d, double rEff) {
        if (d.tempMax() == null || d.tempMin() == null) return 0;
        boolean cycle = d.tempMin() < 0 && d.tempMax() > 0;
        if (!cycle) return 0;
        double dtr = d.tempMax() - d.tempMin();
        int lv = level(dtr, 10, 15, 20, 25);
        if (lv == 0) return 0;
        if (nz(d.rainfall()) >= 10 || rEff >= 40) lv += 1;
        return lv;
    }

    // ---- §4.4 坡面崩塌 ----
    private int collapse(DailyInput d) {
        Double rh = d.rhAvg();
        if (rh == null) return 0;
        int lv;
        if (rh >= 97) lv = 4;
        else if (rh >= 93) lv = 3;
        else if (rh >= 88) lv = 2;
        else if (rh >= 80) lv = 1;
        else lv = 0;
        if (lv == 0) return 0;
        if (nz(d.rainfall()) >= 25) lv += 1;
        return lv;
    }

    // ---- §5 综合风险指数 H (各因子归一到 [0,1] 后加权) ----
    private double compIndex(DailyInput d, double rEff, Double dtr) {
        double fR = norm(nz(d.rainfall()), 70);      // 红色阈值作满分基准
        double fReff = norm(rEff, 200);
        double fRh = d.rhAvg() == null ? 0 : norm(d.rhAvg(), 100);
        double fV = d.windMax() == null ? 0 : norm(d.windMax(), 15);
        double fDtr = dtr == null ? 0 : norm(dtr, 25);
        return W_R * fR + W_REFF * fReff + W_RH * fRh + W_V * fV + W_DTR * fDtr;
    }

    private Double dtr(DailyInput d) {
        if (d.tempMax() == null || d.tempMin() == null) return null;
        return d.tempMax() - d.tempMin();
    }

    /** 通用四阈值分级: < a ->0, [a,b)->1, [b,c)->2, [c,e)->3, >=e ->4 */
    private int level(double v, double a, double b, double c, double e) {
        if (v >= e) return 4;
        if (v >= c) return 3;
        if (v >= b) return 2;
        if (v >= a) return 1;
        return 0;
    }

    private double norm(double v, double full) {
        if (v <= 0) return 0;
        return Math.min(1.0, v / full);
    }

    private int clamp(int lv) { return Math.max(0, Math.min(4, lv)); }
    private double nz(Double v) { return v == null ? 0.0 : v; }
    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
    private double round3(double v) { return Math.round(v * 1000.0) / 1000.0; }
}
