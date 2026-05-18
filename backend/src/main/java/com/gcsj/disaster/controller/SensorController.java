package com.gcsj.disaster.controller;

import com.gcsj.disaster.common.PageResult;
import com.gcsj.disaster.common.Result;
import com.gcsj.disaster.domain.dto.CreateSensorDTO;
import com.gcsj.disaster.domain.vo.SensorVO;
import com.gcsj.disaster.service.ISensorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "传感器")
@RestController
@RequestMapping("/api/sensors")
@RequiredArgsConstructor
public class SensorController {

    private final ISensorService sensorService;

    @Operation(summary = "分页查询传感器")
    @GetMapping
    public Result<PageResult<SensorVO>> page(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String type,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        Pageable pg = PageRequest.of(Math.max(0, page - 1), size, Sort.by(Sort.Direction.DESC, "id"));
        return Result.ok(PageResult.from(sensorService.page(keyword, type, pg), v -> v));
    }

    @Operation(summary = "全部传感器列表")
    @GetMapping("/all")
    public Result<List<SensorVO>> listAll() {
        return Result.ok(sensorService.listAll());
    }

    @Operation(summary = "传感器详情")
    @GetMapping("/{id}")
    public Result<SensorVO> get(@PathVariable Long id) {
        return Result.ok(sensorService.getById(id));
    }

    @Operation(summary = "新增传感器")
    @PostMapping
    public Result<SensorVO> create(@Valid @RequestBody CreateSensorDTO dto) {
        return Result.ok(sensorService.create(dto));
    }

    @Operation(summary = "更新传感器")
    @PutMapping("/{id}")
    public Result<SensorVO> update(@PathVariable Long id, @Valid @RequestBody CreateSensorDTO dto) {
        return Result.ok(sensorService.update(id, dto));
    }

    @Operation(summary = "删除传感器")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sensorService.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "传感器 GeoJSON FeatureCollection (供地图绘制点位)")
    @GetMapping("/geojson")
    public Result<Map<String, Object>> asGeoJson() {
        return Result.ok(sensorService.asGeoJson());
    }
}
