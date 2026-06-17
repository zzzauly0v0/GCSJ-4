package com.gcsj.disaster.controller;

import com.gcsj.disaster.common.Result;
import com.gcsj.disaster.service.IGisDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 四川空间数据 - 行政区 / 河流 / 居民点 GeoJSON 出口
 * gis_layer.source_url 里写好的 /api/regions/geojson 等指针都落到这里
 */
@Tag(name = "空间数据 (GeoJSON)")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GisDataController {

    private final IGisDataService gisDataService;

    @Operation(summary = "行政区 GeoJSON",
            description = "level=1 省 / 2 市 / 3 县; parent=父级 adcode (如 level=2 时传 510000)")
    @GetMapping("/regions/geojson")
    public Result<Map<String, Object>> regions(@RequestParam(required = false) Short level,
                                               @RequestParam(required = false) String parent,
                                               @RequestParam(required = false) String adcode) {
        return Result.ok(gisDataService.regionsAsGeoJson(level, parent, adcode));
    }

    @Operation(summary = "河流 GeoJSON")
    @GetMapping("/rivers/geojson")
    public Result<Map<String, Object>> rivers() {
        return Result.ok(gisDataService.riversAsGeoJson());
    }

    @Operation(summary = "居民点 GeoJSON",
            description = "type 可选 city/county/town")
    @GetMapping("/settlements/geojson")
    public Result<Map<String, Object>> settlements(@RequestParam(required = false) String type) {
        return Result.ok(gisDataService.settlementsAsGeoJson(type));
    }
}
