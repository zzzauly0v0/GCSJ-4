package com.gcsj.disaster.controller;

import com.gcsj.disaster.common.PageResult;
import com.gcsj.disaster.common.Result;
import com.gcsj.disaster.domain.dto.CreateDisasterEventDTO;
import com.gcsj.disaster.domain.vo.DisasterEventVO;
import com.gcsj.disaster.service.IDisasterEventService;
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

@Tag(name = "灾害事件")
@RestController
@RequestMapping("/api/disasters")
@RequiredArgsConstructor
public class DisasterEventController {

    private final IDisasterEventService disasterEventService;

    @Operation(summary = "分页查询灾害事件")
    @GetMapping
    public Result<PageResult<DisasterEventVO>> page(@RequestParam(required = false) Short level,
                                                    @RequestParam(required = false) String type,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int size) {
        Pageable pg = PageRequest.of(Math.max(0, page - 1), size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        return Result.ok(PageResult.from(disasterEventService.page(level, type, pg), v -> v));
    }

    @Operation(summary = "事件详情")
    @GetMapping("/{id}")
    public Result<DisasterEventVO> get(@PathVariable Long id) {
        return Result.ok(disasterEventService.getById(id));
    }

    @Operation(summary = "新建灾害事件")
    @PostMapping
    public Result<DisasterEventVO> create(@Valid @RequestBody CreateDisasterEventDTO dto) {
        return Result.ok(disasterEventService.create(dto));
    }

    @Operation(summary = "更新灾害事件")
    @PutMapping("/{id}")
    public Result<DisasterEventVO> update(@PathVariable Long id, @Valid @RequestBody CreateDisasterEventDTO dto) {
        return Result.ok(disasterEventService.update(id, dto));
    }

    @Operation(summary = "删除灾害事件")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        disasterEventService.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "最新事件")
    @GetMapping("/latest")
    public Result<List<DisasterEventVO>> latest(@RequestParam(defaultValue = "10") int limit) {
        return Result.ok(disasterEventService.latest(limit));
    }

    @Operation(summary = "灾害事件 GeoJSON (含影响区域)")
    @GetMapping("/geojson")
    public Result<Map<String, Object>> asGeoJson() {
        return Result.ok(disasterEventService.asGeoJson());
    }
}
