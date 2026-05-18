package com.gcsj.disaster.controller;

import com.gcsj.disaster.common.PageResult;
import com.gcsj.disaster.common.Result;
import com.gcsj.disaster.domain.dto.CreateAlertDTO;
import com.gcsj.disaster.domain.vo.AlertVO;
import com.gcsj.disaster.service.IAlertService;
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

/**
 * 预警事件
 */
@Tag(name = "预警事件")
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final IAlertService alertService;

    @Operation(summary = "手动创建预警 (并触发分发事件)")
    @PostMapping
    public Result<AlertVO> create(@Valid @RequestBody CreateAlertDTO dto) {
        return Result.ok(alertService.create(dto));
    }

    @Operation(summary = "分页查询预警")
    @GetMapping
    public Result<PageResult<AlertVO>> page(@RequestParam(required = false) Short level,
                                            @RequestParam(required = false) Short status,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        Pageable pg = PageRequest.of(Math.max(0, page - 1), size, Sort.by(Sort.Direction.DESC, "triggeredAt"));
        return Result.ok(PageResult.from(alertService.page(level, status, pg), v -> v));
    }

    @Operation(summary = "预警详情")
    @GetMapping("/{id}")
    public Result<AlertVO> get(@PathVariable Long id) {
        return Result.ok(alertService.getById(id));
    }

    @Operation(summary = "确认预警")
    @PostMapping("/{id}/confirm")
    public Result<AlertVO> confirm(@PathVariable Long id) {
        return Result.ok(alertService.confirm(id));
    }

    @Operation(summary = "关闭预警")
    @PostMapping("/{id}/close")
    public Result<AlertVO> close(@PathVariable Long id) {
        return Result.ok(alertService.close(id));
    }

    @Operation(summary = "最新 N 条预警 (大屏左侧滚动)")
    @GetMapping("/latest")
    public Result<List<AlertVO>> latest(@RequestParam(defaultValue = "10") int limit) {
        return Result.ok(alertService.latest(limit));
    }

    @Operation(summary = "预警 GeoJSON (地图标注)")
    @GetMapping("/geojson")
    public Result<Map<String, Object>> asGeoJson() {
        return Result.ok(alertService.asGeoJson());
    }
}
