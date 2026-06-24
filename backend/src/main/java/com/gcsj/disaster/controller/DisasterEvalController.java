package com.gcsj.disaster.controller;

import com.gcsj.disaster.common.Result;
import com.gcsj.disaster.service.DisasterEvalEngine;
import com.gcsj.disaster.service.DisasterEvalEngine.DailyEval;
import com.gcsj.disaster.service.DisasterEvalEngine.DailyInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

/**
 * 历史气象灾害判别接口。
 * - /run: 读 biz_weather_daily, 逐站调 DisasterEvalEngine, upsert 进 biz_disaster_eval (幂等)
 * - 其余只读接口供前端历史灾害分析页消费
 * - 结果只入 biz_disaster_eval, 不写 biz_disaster_event / biz_alert
 * - 沿用 ReplayController 的 JdbcTemplate 直查模式
 */
@Tag(name = "历史气象灾害判别")
@RestController
@RequestMapping("/api/disaster-eval")
@RequiredArgsConstructor
public class DisasterEvalController {

    private final JdbcTemplate jdbc;
    private final DisasterEvalEngine engine;

    /** 灾种 -> biz_disaster_eval 等级列 (供 /events 按灾种过滤) */
    private static final Map<String, String> TYPE_COL = Map.of(
            "landslide", "landslide_level",
            "mudslide", "mudslide_level",
            "freezethaw", "freezethaw_level",
            "collapse", "collapse_level",
            "comp", "comp_level");

    /** 批算: 读全部(或限定)日观测, 逐站判别, upsert 回填 */
    @Operation(summary = "执行灾害判别批算 (幂等回填 biz_disaster_eval)")
    @PostMapping("/run")
    public Result<Map<String, Object>> run(@RequestParam(required = false) String stationCode,
                                           @RequestParam(required = false) Integer year) {
        StringBuilder sql = new StringBuilder("""
            SELECT station_code, obs_date, rainfall, temp_max, temp_min, rh_avg, wind_max
            FROM   biz.biz_weather_daily
            WHERE  1=1
        """);
        List<Object> args = new ArrayList<>();
        if (stationCode != null && !stationCode.isBlank()) {
            sql.append(" AND station_code = ? ");
            args.add(stationCode);
        }
        if (year != null) {
            sql.append(" AND EXTRACT(YEAR FROM obs_date) = ? ");
            args.add(year);
        }
        sql.append(" ORDER BY station_code, obs_date");

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());

        // 按站点分组 (已按 station_code, obs_date 升序)
        Map<String, List<DailyInput>> byStation = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String code = (String) r.get("station_code");
            LocalDate d = ((Date) r.get("obs_date")).toLocalDate();
            byStation.computeIfAbsent(code, k -> new ArrayList<>()).add(new DailyInput(
                    d, toD(r.get("rainfall")), toD(r.get("temp_max")), toD(r.get("temp_min")),
                    toD(r.get("rh_avg")), toD(r.get("wind_max"))));
        }

        String upsert = """
            INSERT INTO biz.biz_disaster_eval
                (station_code, obs_date, r_eff, dtr, landslide_level, mudslide_level,
                 freezethaw_level, collapse_level, comp_level, comp_index)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (station_code, obs_date) DO UPDATE SET
                r_eff = EXCLUDED.r_eff, dtr = EXCLUDED.dtr,
                landslide_level = EXCLUDED.landslide_level,
                mudslide_level = EXCLUDED.mudslide_level,
                freezethaw_level = EXCLUDED.freezethaw_level,
                collapse_level = EXCLUDED.collapse_level,
                comp_level = EXCLUDED.comp_level,
                comp_index = EXCLUDED.comp_index
        """;

        int written = 0;
        for (Map.Entry<String, List<DailyInput>> e : byStation.entrySet()) {
            String code = e.getKey();
            List<DailyEval> evals = engine.evaluateStation(e.getValue());
            List<Object[]> batch = new ArrayList<>(evals.size());
            for (DailyEval ev : evals) {
                batch.add(new Object[]{code, Date.valueOf(ev.date()), ev.rEff(), ev.dtr(),
                        ev.landslide(), ev.mudslide(), ev.freezethaw(), ev.collapse(),
                        ev.compLevel(), ev.compIndex()});
            }
            for (int c : jdbc.batchUpdate(upsert, batch)) written += Math.abs(c);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stations", byStation.size());
        out.put("rowsWritten", written);
        return Result.ok(out);
    }

    /** 单站逐日气象 + 风险时序 */
    @Operation(summary = "单站逐日气象+风险时序")
    @GetMapping("/series")
    public Result<List<Map<String, Object>>> series(@RequestParam String stationCode,
                                                     @RequestParam String from,
                                                     @RequestParam String to) {
        return Result.ok(jdbc.queryForList("""
            SELECT w.obs_date, w.rainfall, w.temp_avg, w.temp_max, w.temp_min, w.rh_avg, w.wind_max,
                   e.r_eff, e.comp_level, e.landslide_level, e.mudslide_level,
                   e.freezethaw_level, e.collapse_level
            FROM   biz.biz_weather_daily w
            LEFT   JOIN biz.biz_disaster_eval e
                   ON e.station_code = w.station_code AND e.obs_date = w.obs_date
            WHERE  w.station_code = ?
              AND  w.obs_date BETWEEN ?::date AND ?::date
            ORDER BY w.obs_date
        """, stationCode, from, to));
    }

    /** 站点列表 + 经纬度 + 覆盖年份 + 历史最高综合等级 (供地图打点) */
    @Operation(summary = "站点列表 (含历史最高风险等级)")
    @GetMapping("/stations")
    public Result<List<Map<String, Object>>> stations(@RequestParam(required = false) Integer year) {
        String yearFilter = year != null ? " AND EXTRACT(YEAR FROM e.obs_date) = " + year + " " : "";
        return Result.ok(jdbc.queryForList("""
            SELECT s.code, s.name, s.region_code, s.year_coverage, s.record_count,
                   ST_X(s.location) AS lon, ST_Y(s.location) AS lat,
                   COALESCE(MAX(e.comp_level), 0) AS max_level
            FROM   biz.biz_monitor_station s
            LEFT   JOIN biz.biz_disaster_eval e
                   ON e.station_code = s.code """ + yearFilter + """
            WHERE  s.type = 'weather'
            GROUP BY s.code, s.name, s.region_code, s.year_coverage, s.record_count, s.location
            ORDER BY max_level DESC, s.code
        """));
    }

    /** 风险日列表 (comp_level>=1 或指定灾种), 分页 */
    @Operation(summary = "风险日列表 (分页)")
    @GetMapping("/events")
    public Result<Map<String, Object>> events(@RequestParam(required = false) Integer year,
                                              @RequestParam(required = false) Integer level,
                                              @RequestParam(required = false) String type,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        String col = TYPE_COL.getOrDefault(type, "comp_level");
        StringBuilder where = new StringBuilder(" WHERE e." + col + " >= ? ");
        List<Object> args = new ArrayList<>();
        args.add(level != null ? level : 1);
        if (year != null) { where.append(" AND EXTRACT(YEAR FROM e.obs_date) = ? "); args.add(year); }

        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM biz.biz_disaster_eval e" + where, Integer.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        List<Map<String, Object>> list = jdbc.queryForList("""
            SELECT e.station_code, s.name AS station_name, e.obs_date,
                   e.r_eff, e.landslide_level, e.mudslide_level,
                   e.freezethaw_level, e.collapse_level, e.comp_level, e.comp_index
            FROM   biz.biz_disaster_eval e
            LEFT   JOIN biz.biz_monitor_station s ON s.code = e.station_code
        """ + where + " ORDER BY e.comp_level DESC, e.obs_date DESC LIMIT ? OFFSET ?",
                pageArgs.toArray());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("list", list);
        return Result.ok(out);
    }

    /** 大屏概览聚合: 数据规模 + 灾种风险日分布 + 等级分布 + 逐年趋势 (供监测大屏历史面板) */
    @Operation(summary = "历史气象灾害概览聚合 (供大屏)")
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(@RequestParam(required = false) Integer year) {
        String wYear = year != null ? " AND EXTRACT(YEAR FROM obs_date) = " + year + " " : "";

        Map<String, Object> out = new LinkedHashMap<>();

        // 数据规模
        out.put("stationCount", jdbc.queryForObject(
                "SELECT COUNT(*) FROM biz.biz_monitor_station WHERE type='weather'", Integer.class));
        out.put("weatherDays", jdbc.queryForObject(
                "SELECT COUNT(*) FROM biz.biz_weather_daily WHERE 1=1" + wYear, Integer.class));
        out.put("riskDays", jdbc.queryForObject(
                "SELECT COUNT(*) FROM biz.biz_disaster_eval WHERE comp_level >= 1" + wYear, Integer.class));

        // 灾种风险日分布 (level>=1 计为一次) -> 饼图
        Map<String, Object> byType = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : Map.of(
                "landslide", "landslide_level", "mudslide", "mudslide_level",
                "freezethaw", "freezethaw_level", "collapse", "collapse_level").entrySet()) {
            Integer c = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM biz.biz_disaster_eval WHERE " + e.getValue() + " >= 1" + wYear,
                    Integer.class);
            byType.put(e.getKey(), c);
        }
        out.put("byType", byType);

        // 综合等级分布 (1蓝..4红) -> 等级卡
        out.put("byLevel", jdbc.queryForList(
                "SELECT comp_level AS level, COUNT(*) AS cnt FROM biz.biz_disaster_eval " +
                "WHERE comp_level >= 1" + wYear + " GROUP BY comp_level ORDER BY comp_level"));

        // 逐年风险日趋势 -> 折线/柱
        out.put("byYear", jdbc.queryForList(
                "SELECT EXTRACT(YEAR FROM obs_date)::int AS year, COUNT(*) AS cnt " +
                "FROM biz.biz_disaster_eval WHERE comp_level >= 1 GROUP BY year ORDER BY year"));

        // Top 风险日 (跨年, 综合等级最高若干条) -> 大屏列表
        out.put("topEvents", jdbc.queryForList("""
            SELECT e.station_code, s.name AS station_name, e.obs_date,
                   e.r_eff, e.comp_level
            FROM   biz.biz_disaster_eval e
            LEFT   JOIN biz.biz_monitor_station s ON s.code = e.station_code
            WHERE  e.comp_level >= 1
        """ + (year != null ? " AND EXTRACT(YEAR FROM e.obs_date) = " + year + " " : "") +
            " ORDER BY e.comp_level DESC, e.r_eff DESC NULLS LAST, e.obs_date DESC LIMIT 8"));

        return Result.ok(out);
    }

    /** 区县级聚合 (供专题热力) */
    @Operation(summary = "区县级气象/风险聚合")
    @GetMapping("/heatmap")
    public Result<List<Map<String, Object>>> heatmap(@RequestParam(required = false) Integer year,
                                                     @RequestParam(defaultValue = "rainfall") String metric) {
        String agg = "rainfall".equals(metric)
                ? "AVG(w.rainfall) AS value"
                : "AVG(e.comp_level) AS value";
        String yearFilter = year != null ? " AND EXTRACT(YEAR FROM w.obs_date) = " + year + " " : "";
        return Result.ok(jdbc.queryForList("""
            SELECT s.region_code, r.name AS region_name, """ + agg + """
            FROM   biz.biz_weather_daily w
            JOIN   biz.biz_monitor_station s ON s.code = w.station_code
            LEFT   JOIN biz.biz_disaster_eval e ON e.station_code = w.station_code AND e.obs_date = w.obs_date
            LEFT   JOIN gis.gis_admin_region r ON r.adcode = s.region_code
            WHERE  s.type = 'weather' """ + yearFilter + """
            GROUP BY s.region_code, r.name
            ORDER BY value DESC NULLS LAST
        """));
    }

    private static Double toD(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (NumberFormatException ex) { return null; }
    }
}
