package com.gcsj.disaster.controller;

import com.gcsj.disaster.common.Result;
import com.gcsj.disaster.service.IWeatherIngestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据采集 - 手动触发气象拉取
 */
@Tag(name = "数据采集")
@RestController
@RequestMapping("/api/ingest")
@RequiredArgsConstructor
public class WeatherIngestController {

    private final IWeatherIngestService weatherIngestService;

    @Operation(summary = "立即拉取一次气象数据")
    @PostMapping("/weather")
    public Result<Integer> pullWeather() {
        return Result.ok(weatherIngestService.pullOnce());
    }
}
