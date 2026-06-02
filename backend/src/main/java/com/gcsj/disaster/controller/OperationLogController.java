package com.gcsj.disaster.controller;

import com.gcsj.disaster.common.PageResult;
import com.gcsj.disaster.common.Result;
import com.gcsj.disaster.domain.vo.OperationLogVO;
import com.gcsj.disaster.service.IOperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@Tag(name = "操作日志")
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class OperationLogController {

    private final IOperationLogService logService;

    @Operation(summary = "分页查询操作日志 (按时间倒序)")
    @GetMapping
    public Result<PageResult<OperationLogVO>> page(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String uri,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return Result.ok(logService.page(username, method, uri, from, to, page, size));
    }
}
