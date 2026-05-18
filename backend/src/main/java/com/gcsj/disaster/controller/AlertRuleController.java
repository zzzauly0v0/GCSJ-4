package com.gcsj.disaster.controller;

import com.gcsj.disaster.common.Result;
import com.gcsj.disaster.domain.dto.CreateAlertRuleDTO;
import com.gcsj.disaster.domain.vo.AlertRuleVO;
import com.gcsj.disaster.service.IAlertRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "预警规则")
@RestController
@RequestMapping("/api/alert-rules")
@RequiredArgsConstructor
public class AlertRuleController {

    private final IAlertRuleService alertRuleService;

    @Operation(summary = "全部规则")
    @GetMapping
    public Result<List<AlertRuleVO>> list() {
        return Result.ok(alertRuleService.listAll());
    }

    @Operation(summary = "新建规则")
    @PostMapping
    public Result<AlertRuleVO> create(@Valid @RequestBody CreateAlertRuleDTO dto) {
        return Result.ok(alertRuleService.create(dto));
    }

    @Operation(summary = "更新规则")
    @PutMapping("/{id}")
    public Result<AlertRuleVO> update(@PathVariable Long id, @Valid @RequestBody CreateAlertRuleDTO dto) {
        return Result.ok(alertRuleService.update(id, dto));
    }

    @Operation(summary = "删除规则")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        alertRuleService.deleteById(id);
        return Result.ok();
    }
}
