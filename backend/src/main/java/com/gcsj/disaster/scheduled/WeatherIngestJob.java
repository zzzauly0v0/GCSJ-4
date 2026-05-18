package com.gcsj.disaster.scheduled;

import com.gcsj.disaster.service.IWeatherIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 气象数据定时采集
 * 默认每 60s 执行一次模拟拉取 (生产可调整 cron)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherIngestJob {

    private final IWeatherIngestService weatherIngestService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    public void run() {
        try {
            int n = weatherIngestService.pullOnce();
            if (n > 0) log.debug("weather job ingested {} obs", n);
        } catch (Exception e) {
            log.warn("weather job failed: {}", e.getMessage());
        }
    }
}
