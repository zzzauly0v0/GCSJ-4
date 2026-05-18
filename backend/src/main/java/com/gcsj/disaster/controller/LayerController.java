package com.gcsj.disaster.controller;

import com.gcsj.disaster.common.Result;
import com.gcsj.disaster.domain.dto.CreateLayerDTO;
import com.gcsj.disaster.domain.vo.LayerVO;
import com.gcsj.disaster.service.ILayerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 空间数据 - 图层管理
 */
@Tag(name = "图层管理")
@RestController
@RequestMapping("/api/layers")
@RequiredArgsConstructor
public class LayerController {

    private final ILayerService layerService;

    @Operation(summary = "全部图层")
    @GetMapping
    public Result<List<LayerVO>> list() {
        return Result.ok(layerService.listAll());
    }

    @Operation(summary = "图层详情")
    @GetMapping("/{id}")
    public Result<LayerVO> get(@PathVariable Long id) {
        return Result.ok(layerService.getById(id));
    }

    @Operation(summary = "新增图层")
    @PostMapping
    public Result<LayerVO> create(@Valid @RequestBody CreateLayerDTO dto) {
        return Result.ok(layerService.create(dto));
    }

    @Operation(summary = "更新图层")
    @PutMapping("/{id}")
    public Result<LayerVO> update(@PathVariable Long id, @Valid @RequestBody CreateLayerDTO dto) {
        return Result.ok(layerService.update(id, dto));
    }

    @Operation(summary = "删除图层")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        layerService.deleteById(id);
        return Result.ok();
    }
}
