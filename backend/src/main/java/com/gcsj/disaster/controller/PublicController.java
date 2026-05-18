package com.gcsj.disaster.controller;

import com.gcsj.disaster.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 公开接口 — 无需登录
 */
@Tag(name = "公开")
@RestController
@RequestMapping("/api/public")
public class PublicController {

    @Operation(summary = "服务健康检查")
    @GetMapping("/ping")
    public Result<Map<String, Object>> ping() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("service", "gcsj-disaster-backend");
        m.put("status", "UP");
        m.put("ts", System.currentTimeMillis());
        return Result.ok(m);
    }
}
