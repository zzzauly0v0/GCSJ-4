package com.gcsj.disaster.controller;

import com.gcsj.disaster.common.Result;
import com.gcsj.disaster.domain.dto.CreateEmergencyPlanDTO;
import com.gcsj.disaster.domain.vo.EmergencyPlanVO;
import com.gcsj.disaster.service.IEmergencyPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "应急预案")
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class EmergencyPlanController {

    private final IEmergencyPlanService planService;

    @Operation(summary = "全部预案")
    @GetMapping
    public Result<List<EmergencyPlanVO>> list() {
        return Result.ok(planService.listAll());
    }

    @Operation(summary = "预案详情")
    @GetMapping("/{id}")
    public Result<EmergencyPlanVO> get(@PathVariable Long id) {
        return Result.ok(planService.getById(id));
    }

    @Operation(summary = "新增预案")
    @PostMapping
    public Result<EmergencyPlanVO> create(@Valid @RequestBody CreateEmergencyPlanDTO dto) {
        return Result.ok(planService.create(dto));
    }

    @Operation(summary = "更新预案")
    @PutMapping("/{id}")
    public Result<EmergencyPlanVO> update(@PathVariable Long id, @Valid @RequestBody CreateEmergencyPlanDTO dto) {
        return Result.ok(planService.update(id, dto));
    }

    @Operation(summary = "删除预案")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        planService.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "查找适用预案 (按灾种 + 等级)")
    @GetMapping("/applicable")
    public Result<List<EmergencyPlanVO>> applicable(@RequestParam String disasterType,
                                                    @RequestParam Short level) {
        return Result.ok(planService.findApplicable(disasterType, level));
    }
}
