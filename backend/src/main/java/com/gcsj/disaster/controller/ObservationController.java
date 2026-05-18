package com.gcsj.disaster.controller;

import com.gcsj.disaster.common.PageResult;
import com.gcsj.disaster.common.Result;
import com.gcsj.disaster.domain.dto.CreateObservationDTO;
import com.gcsj.disaster.domain.vo.ObservationVO;
import com.gcsj.disaster.service.IObservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 实时监测 - 观测数据
 */
@Tag(name = "实时监测")
@RestController
@RequestMapping("/api/observations")
@RequiredArgsConstructor
public class ObservationController {

    private final IObservationService observationService;

    @Operation(summary = "上报观测数据 (写入 + 触发规则评估事件)")
    @PostMapping
    public Result<ObservationVO> ingest(@Valid @RequestBody CreateObservationDTO dto) {
        return Result.ok(observationService.ingest(dto));
    }

    @Operation(summary = "分页观测记录")
    @GetMapping
    public Result<PageResult<ObservationVO>> page(@RequestParam(required = false) Long sensorId,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        Pageable pg = PageRequest.of(Math.max(0, page - 1), size, Sort.by(Sort.Direction.DESC, "observedAt"));
        return Result.ok(PageResult.from(observationService.page(sensorId, pg), v -> v));
    }

    @Operation(summary = "时间序列 (echarts 折线)")
    @GetMapping("/series")
    public Result<List<ObservationVO>> series(@RequestParam Long sensorId,
                                              @RequestParam String indicator,
                                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
                                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return Result.ok(observationService.series(sensorId, indicator, from, to));
    }

    @Operation(summary = "最新 N 条观测")
    @GetMapping("/latest")
    public Result<List<ObservationVO>> latest(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(observationService.latest(limit));
    }
}
