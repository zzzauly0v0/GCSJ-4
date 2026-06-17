package com.gcsj.disaster.controller;

import com.gcsj.disaster.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 时间轴回放接口
 * - 数据由 spatial_analyse/replay_data_gen.py 生成 (2022-09-05~07 泸定地震窗口)
 * - 预警/事件已在 Python 离线计算入库, 后端只做按虚拟时间过滤的查询
 * - 故意用 JdbcTemplate 直查, 不引入 entity/service/repo 三件套
 */
@Tag(name = "时间轴回放")
@RestController
@RequestMapping("/api/replay")
@RequiredArgsConstructor
public class ReplayController {

    private final JdbcTemplate jdbc;

    /** 回放数据窗口元信息 (前端时间轴用) */
    @Operation(summary = "回放窗口元信息")
    @GetMapping("/window")
    public Result<Map<String, Object>> window() {
        Map<String, Object> r = jdbc.queryForMap("""
            SELECT MIN(observed_at) start_at,
                   MAX(observed_at) end_at,
                   COUNT(*) total_obs
            FROM   biz.biz_weather_observation
        """);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("startAt", r.get("start_at"));
        out.put("endAt",   r.get("end_at"));
        out.put("description", "2022-09-05 ~ 09-07 四川泸定 6.8 级地震窗口");
        return Result.ok(out);
    }

    /** 某虚拟时刻的全量快照: 各市州雨量 + 活跃预警数 */
    @Operation(summary = "快照: 给定虚拟时刻的全省概览")
    @GetMapping("/snapshot")
    public Result<Map<String, Object>> snapshot(@RequestParam("at") OffsetDateTime at) {
        // 各市州 1h 雨强 (取最近 1 小时)
        List<Map<String, Object>> rain = jdbc.queryForList("""
            SELECT s.region_code,
                   r.name AS region_name,
                   AVG(w.rainfall_1h) AS rainfall_1h,
                   AVG(w.rainfall_24h) AS rainfall_24h
            FROM   biz.biz_weather_observation w
            JOIN   biz.biz_monitor_station s ON s.code = w.station_code
            JOIN   gis.gis_admin_region    r ON r.adcode = s.region_code
            WHERE  s.type = 'weather'
              AND  w.observed_at = (
                    SELECT MAX(observed_at) FROM biz.biz_weather_observation
                    WHERE observed_at <= ?
              )
            GROUP BY s.region_code, r.name
            ORDER BY rainfall_24h DESC NULLS LAST
        """, at);

        Integer activeAlerts = jdbc.queryForObject("""
            SELECT COUNT(*) FROM biz.biz_alert
            WHERE  triggered_at <= ?
              AND  (sent_at IS NULL OR sent_at <= ?)
              AND  deleted = false
        """, Integer.class, at, at);

        Integer activeEvents = jdbc.queryForObject("""
            SELECT COUNT(*) FROM biz.biz_disaster_event
            WHERE  occurred_at <= ?
              AND  deleted = false
        """, Integer.class, at);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("at", at);
        out.put("rainByRegion", rain);
        out.put("activeAlerts", activeAlerts);
        out.put("activeEvents", activeEvents);
        return Result.ok(out);
    }

    /** 某站点最近 N 小时时序 (前端 ECharts 折线) */
    @Operation(summary = "气象站时序: 站码 + 截止虚拟时刻 + 回看小时数")
    @GetMapping("/weather/series")
    public Result<List<Map<String, Object>>> weatherSeries(
            @RequestParam String stationCode,
            @RequestParam OffsetDateTime at,
            @RequestParam(defaultValue = "24") int hours) {
        return Result.ok(jdbc.queryForList("""
            SELECT observed_at AS ts,
                   rainfall_1h, rainfall_24h, temperature, humidity
            FROM   biz.biz_weather_observation
            WHERE  station_code = ?
              AND  observed_at <= ?
              AND  observed_at >= ? - make_interval(hours => ?)
            ORDER BY observed_at
        """, stationCode, at, at, hours));
    }

    /** 地质传感器时序 (单站单指标) */
    @Operation(summary = "地质传感器时序")
    @GetMapping("/geo/series")
    public Result<List<Map<String, Object>>> geoSeries(
            @RequestParam String stationCode,
            @RequestParam String metric,
            @RequestParam OffsetDateTime at,
            @RequestParam(defaultValue = "24") int hours) {
        return Result.ok(jdbc.queryForList("""
            SELECT observed_at AS ts, value, is_anomaly
            FROM   biz.biz_geo_sensor_reading
            WHERE  station_code = ?
              AND  metric_type  = ?
              AND  observed_at <= ?
              AND  observed_at >= ? - make_interval(hours => ?)
            ORDER BY observed_at
        """, stationCode, metric, at, at, hours));
    }

    /** 截止虚拟时刻的活跃预警列表 */
    @Operation(summary = "活跃预警 (按虚拟时刻过滤)")
    @GetMapping("/alerts")
    public Result<List<Map<String, Object>>> alerts(
            @RequestParam OffsetDateTime at,
            @RequestParam(defaultValue = "100") int limit) {
        return Result.ok(jdbc.queryForList("""
            SELECT a.id, a.code, a.title, a.content, a.level, a.source,
                   a.triggered_at, a.event_id,
                   ST_X(a.location) AS lon, ST_Y(a.location) AS lat,
                   e.type AS event_type, e.region_code
            FROM   biz.biz_alert a
            LEFT   JOIN biz.biz_disaster_event e ON e.id = a.event_id
            WHERE  a.triggered_at <= ?
              AND  a.deleted = false
            ORDER BY a.triggered_at DESC
            LIMIT  ?
        """, at, limit));
    }

    /** 截止虚拟时刻的灾害事件 GeoJSON (供地图渲染) */
    @Operation(summary = "灾害事件 GeoJSON (按虚拟时刻过滤)")
    @GetMapping("/events/geojson")
    public Result<Map<String, Object>> eventsGeoJson(@RequestParam OffsetDateTime at) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id, code, title, type, level, region_code,
                   occurred_at,
                   ST_X(location) AS lon, ST_Y(location) AS lat,
                   description
            FROM   biz.biz_disaster_event
            WHERE  occurred_at <= ?
              AND  deleted = false
            ORDER BY occurred_at DESC
        """, at);

        List<Map<String, Object>> features = rows.stream().map(row -> {
            Map<String, Object> geom = new LinkedHashMap<>();
            geom.put("type", "Point");
            geom.put("coordinates", List.of(row.get("lon"), row.get("lat")));
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("id", row.get("id"));
            props.put("code", row.get("code"));
            props.put("title", row.get("title"));
            props.put("type", row.get("type"));
            props.put("level", row.get("level"));
            props.put("regionCode", row.get("region_code"));
            props.put("occurredAt", row.get("occurred_at"));
            props.put("description", row.get("description"));
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("type", "Feature");
            f.put("geometry", geom);
            f.put("properties", props);
            return f;
        }).toList();

        Map<String, Object> fc = new LinkedHashMap<>();
        fc.put("type", "FeatureCollection");
        fc.put("features", features);
        return Result.ok(fc);
    }

    /** 地震事件列表 (按虚拟时刻过滤) */
    @Operation(summary = "地震事件 (按虚拟时刻过滤)")
    @GetMapping("/earthquakes")
    public Result<List<Map<String, Object>>> earthquakes(@RequestParam OffsetDateTime at) {
        return Result.ok(jdbc.queryForList("""
            SELECT id, code, occurred_at, magnitude, depth_km,
                   ST_X(epicenter) AS lon, ST_Y(epicenter) AS lat,
                   location_name, region_code
            FROM   biz.biz_earthquake_event
            WHERE  occurred_at <= ?
            ORDER BY occurred_at DESC
        """, at));
    }

    /** 全部气象站某虚拟时刻的雨量 (供地图染色 + 热力层) */
    @Operation(summary = "全省气象站雨量快照 (供热力图)")
    @GetMapping("/weather/snapshot")
    public Result<List<Map<String, Object>>> weatherSnapshot(@RequestParam OffsetDateTime at) {
        return Result.ok(jdbc.queryForList("""
            SELECT s.code, s.name, s.region_code,
                   ST_X(s.location) AS lon, ST_Y(s.location) AS lat,
                   w.rainfall_1h, w.rainfall_24h, w.temperature, w.humidity, w.wind_speed
            FROM   biz.biz_monitor_station s
            JOIN   biz.biz_weather_observation w
                   ON w.station_code = s.code
                  AND w.observed_at  = (
                        SELECT MAX(observed_at) FROM biz.biz_weather_observation w2
                        WHERE w2.station_code = s.code AND w2.observed_at <= ?
                  )
            WHERE  s.type = 'weather'
            ORDER  BY w.rainfall_24h DESC NULLS LAST
        """, at));
    }

    /** 监测站列表 (静态, 不随时间变化) */
    @Operation(summary = "监测站列表")
    @GetMapping("/stations")
    public Result<List<Map<String, Object>>> stations(@RequestParam(required = false) String type) {
        if (type == null || type.isBlank()) {
            return Result.ok(jdbc.queryForList("""
                SELECT code, name, type, region_code,
                       ST_X(location) AS lon, ST_Y(location) AS lat, elevation
                FROM   biz.biz_monitor_station
                ORDER BY type, code
            """));
        }
        return Result.ok(jdbc.queryForList("""
            SELECT code, name, type, region_code,
                   ST_X(location) AS lon, ST_Y(location) AS lat, elevation
            FROM   biz.biz_monitor_station
            WHERE  type = ?
            ORDER BY code
        """, type));
    }
}
