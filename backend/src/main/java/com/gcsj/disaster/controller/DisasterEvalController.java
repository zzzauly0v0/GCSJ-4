package com.gcsj.disaster.controller;

import com.gcsj.disaster.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/**
 * 历史气象地质灾害判别接口 (只读)。
 * 数据分层: gis = 原始气象数据源 (gis_weather_station / gis_weather_daily), biz = 分析结果 (biz_disaster_eval)。
 * - biz_disaster_eval 由 spatial_analyse/merged_disaster_eval.py 回填 (TRIGRS+CRI 滑坡 + 暴雨/高温/干旱), 后端不重算
 * - /push: 回放到某日时推送该日高等级(橙/红)风险事件到 WebSocket /topic/disasters
 * - 其余只读接口供前端历史灾害分析页 / 预警管理页消费
 * - 用 JdbcTemplate 直查, 不引入 entity/service/repo 三件套
 * 列语义 (列名沿用早期命名): landslide=CRI滑坡, mudslide=暴雨, freezethaw=高温, collapse=干旱, comp=四类取最高。
 */
@Tag(name = "历史气象灾害判别")
@RestController
@RequestMapping("/api/disaster-eval")
@RequiredArgsConstructor
public class DisasterEvalController {

    private final JdbcTemplate jdbc;
    private final SimpMessagingTemplate messaging;

    /** 灾种 -> biz_disaster_eval 等级列 (供 /events、/stats 按灾种过滤) */
    private static final Map<String, String> TYPE_COL = Map.of(
            "landslide", "landslide_level",
            "mudslide", "mudslide_level",
            "freezethaw", "freezethaw_level",
            "collapse", "collapse_level",
            "comp", "comp_level");

    /**
     * 把请求参数 type 解析为等级列名; type 为空或未知一律回退到 comp_level。
     * 注意: 不能直接用 TYPE_COL.getOrDefault(type, ...), Map.of 生成的不可变 Map
     *       在 key 为 null 时会抛 NPE (即便是 getOrDefault)。
     */
    private static String levelCol(String type) {
        if (type == null) return "comp_level";
        return TYPE_COL.getOrDefault(type, "comp_level");
    }

    /**
     * 按日推送: 查该日高等级(橙/红, comp_level>=minLevel)风险事件 + 站点经纬度,
     * 通过 WebSocket /topic/disasters 广播给前端 (供时间轴回放到某天时模拟实时预警)。
     * 前端回放到某日时调用本接口。
     */
    @Operation(summary = "按日推送高等级风险事件 (WebSocket /topic/disasters)")
    @PostMapping("/push")
    public Result<Map<String, Object>> push(@RequestParam String date,
                                             @RequestParam(defaultValue = "3") int minLevel) {
        List<Map<String, Object>> events = jdbc.queryForList("""
            SELECT e.station_code, s.name AS station_name, e.obs_date,
                   ST_X(s.location) AS lon, ST_Y(s.location) AS lat,
                   s.region_code, e.r_eff, e.dtr,
                   e.landslide_level, e.mudslide_level, e.freezethaw_level,
                   e.collapse_level, e.comp_level
            FROM   biz.biz_disaster_eval e
            LEFT   JOIN gis.gis_weather_station s ON s.code = e.station_code
            WHERE  e.obs_date = ?::date
              AND  e.comp_level >= ?
            ORDER BY e.comp_level DESC, e.r_eff DESC NULLS LAST
        """, date, minLevel);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("date", date);
        payload.put("count", events.size());
        payload.put("events", events);
        messaging.convertAndSend("/topic/disasters", payload);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", date);
        out.put("pushed", events.size());
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
            FROM   gis.gis_weather_daily w
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
        return Result.ok(jdbc.queryForList(
                "SELECT s.code, s.name, s.region_code, s.year_coverage, s.record_count, " +
                "       ST_X(s.location) AS lon, ST_Y(s.location) AS lat, " +
                "       COALESCE(MAX(e.comp_level), 0) AS max_level " +
                "FROM gis.gis_weather_station s " +
                "LEFT JOIN biz.biz_disaster_eval e ON e.station_code = s.code " + yearFilter + " " +
                "GROUP BY s.code, s.name, s.region_code, s.year_coverage, s.record_count, s.location " +
                "ORDER BY max_level DESC, s.code"));
    }

    /** 风险日列表 (comp_level>=1 或指定灾种), 分页 */
    @Operation(summary = "风险日列表 (分页)")
    @GetMapping("/events")
    public Result<Map<String, Object>> events(@RequestParam(required = false) Integer year,
                                              @RequestParam(required = false) Integer level,
                                              @RequestParam(required = false) String type,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        String col = levelCol(type);
        // 未指定级别: 展示全部风险日 (>=1); 指定级别: 精确匹配该预警级别 (蓝/黄/橙/红)
        StringBuilder where = new StringBuilder(level != null
                ? " WHERE e." + col + " = ? "
                : " WHERE e." + col + " >= ? ");
        List<Object> args = new ArrayList<>();
        args.add(level != null ? level : 1);
        if (year != null) { where.append(" AND EXTRACT(YEAR FROM e.obs_date) = ? "); args.add(year); }

        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM biz.biz_disaster_eval e" + where, Integer.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        List<Map<String, Object>> list = jdbc.queryForList("""
            SELECT e.station_code, s.name AS station_name, s.region_code,
                   r.name AS region_name, e.obs_date,
                   e.r_eff, e.dtr, e.landslide_level, e.mudslide_level,
                   e.freezethaw_level, e.collapse_level, e.comp_level
            FROM   biz.biz_disaster_eval e
            LEFT   JOIN gis.gis_weather_station s ON s.code = e.station_code
            LEFT   JOIN gis.gis_admin_region r ON r.adcode = s.region_code
        """ + where + " ORDER BY e." + col + " DESC, e.obs_date DESC LIMIT ? OFFSET ?",
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
                "SELECT COUNT(*) FROM gis.gis_weather_station", Integer.class));
        out.put("weatherDays", jdbc.queryForObject(
                "SELECT COUNT(*) FROM gis.gis_weather_daily WHERE 1=1" + wYear, Integer.class));
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
            LEFT   JOIN gis.gis_weather_station s ON s.code = e.station_code
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
        return Result.ok(jdbc.queryForList(
                "SELECT s.region_code, r.name AS region_name, " + agg + " " +
                "FROM gis.gis_weather_daily w " +
                "JOIN gis.gis_weather_station s ON s.code = w.station_code " +
                "LEFT JOIN biz.biz_disaster_eval e ON e.station_code = w.station_code AND e.obs_date = w.obs_date " +
                "LEFT JOIN gis.gis_admin_region r ON r.adcode = s.region_code " +
                "WHERE 1=1 " + yearFilter + " " +
                "GROUP BY s.region_code, r.name " +
                "ORDER BY value DESC NULLS LAST"));
    }

    /**
     * 预警统计 (供预警管理页 tab 徽标 + 顶部统计卡):
     *   total   = 风险日总数 (comp_level>=1)
     *   byLevel = [{level, cnt}] 综合等级分布
     *   byType  = {landslide, mudslide, freezethaw, collapse} 各灾种风险日数 (level>=1)
     * 可选按 year / type (灾种) 过滤; type 指定时 total/byLevel 以该灾种等级列为准。
     */
    @Operation(summary = "预警统计 (等级分布 + 灾种分布)")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(@RequestParam(required = false) Integer year,
                                             @RequestParam(required = false) String type) {
        String levelCol = levelCol(type);
        String wYear = year != null ? " AND EXTRACT(YEAR FROM obs_date) = " + year + " " : "";

        Map<String, Object> out = new LinkedHashMap<>();

        out.put("total", jdbc.queryForObject(
                "SELECT COUNT(*) FROM biz.biz_disaster_eval WHERE " + levelCol + " >= 1" + wYear,
                Integer.class));

        out.put("byLevel", jdbc.queryForList(
                "SELECT " + levelCol + " AS level, COUNT(*) AS cnt FROM biz.biz_disaster_eval " +
                "WHERE " + levelCol + " >= 1" + wYear + " GROUP BY " + levelCol + " ORDER BY " + levelCol));

        Map<String, Object> byType = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : Map.of(
                "landslide", "landslide_level", "mudslide", "mudslide_level",
                "freezethaw", "freezethaw_level", "collapse", "collapse_level").entrySet()) {
            byType.put(e.getKey(), jdbc.queryForObject(
                    "SELECT COUNT(*) FROM biz.biz_disaster_eval WHERE " + e.getValue() + " >= 1" + wYear,
                    Integer.class));
        }
        out.put("byType", byType);

        return Result.ok(out);
    }

    /**
     * 单条风险日详情 (供预警详情弹窗): 判别结果 + 当日气象观测 + 当月累计降水统计。
     * eval:    biz_disaster_eval 行 + 站点经纬度 / 高程
     * weather: 当日 gis_weather_daily 观测
     * monthly: 该站当月累计降水 / 天数 / 无雨日数 (供干旱判别展示)
     */
    @Operation(summary = "单条风险日详情 (气象+地形+判别依据)")
    @GetMapping("/detail")
    public Result<Map<String, Object>> detail(@RequestParam String stationCode,
                                              @RequestParam String obsDate) {
        Map<String, Object> out = new LinkedHashMap<>();

        List<Map<String, Object>> evalRows = jdbc.queryForList("""
            SELECT e.station_code, e.obs_date, e.r_eff, e.dtr,
                   e.landslide_level, e.mudslide_level, e.freezethaw_level,
                   e.collapse_level, e.comp_level,
                   ST_X(s.location) AS lon, ST_Y(s.location) AS lat,
                   s.elevation AS station_elevation, s.name AS station_name, s.region_code
            FROM   biz.biz_disaster_eval e
            LEFT   JOIN gis.gis_weather_station s ON s.code = e.station_code
            WHERE  e.station_code = ? AND e.obs_date = ?::date
        """, stationCode, obsDate);
        out.put("eval", evalRows.isEmpty() ? null : evalRows.get(0));

        List<Map<String, Object>> wRows = jdbc.queryForList("""
            SELECT obs_date, rainfall, temp_avg, temp_max, temp_min, rh_avg, wind_max
            FROM   gis.gis_weather_daily
            WHERE  station_code = ? AND obs_date = ?::date
        """, stationCode, obsDate);
        out.put("weather", wRows.isEmpty() ? null : wRows.get(0));

        LocalDate d = LocalDate.parse(obsDate);
        out.put("month", d.getMonthValue());
        out.put("monthly", jdbc.queryForMap("""
            SELECT COALESCE(SUM(rainfall), 0) AS monthly_rain,
                   COUNT(*) AS days,
                   COALESCE(SUM(CASE WHEN COALESCE(rainfall, 0) < 0.1 THEN 1 ELSE 0 END), 0) AS dry_days
            FROM   gis.gis_weather_daily
            WHERE  station_code = ?
              AND  EXTRACT(YEAR FROM obs_date) = ?
              AND  EXTRACT(MONTH FROM obs_date) = ?
        """, stationCode, d.getYear(), d.getMonthValue()));

        return Result.ok(out);
    }
}