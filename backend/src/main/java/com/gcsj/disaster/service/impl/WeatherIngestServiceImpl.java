package com.gcsj.disaster.service.impl;

import com.gcsj.disaster.domain.dto.CreateObservationDTO;
import com.gcsj.disaster.domain.entity.Sensor;
import com.gcsj.disaster.repository.SensorRepository;
import com.gcsj.disaster.service.IObservationService;
import com.gcsj.disaster.service.IWeatherIngestService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Random;

/**
 * 气象数据采集 - 默认提供模拟实现
 * 真实接入: 替换 doFetch() 即可 (扩展点)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherIngestServiceImpl implements IWeatherIngestService {

    private final WeatherProps props;
    private final SensorRepository sensorRepository;
    private final IObservationService observationService;
    private final Random random = new Random();

    @Override
    public int pullOnce() {
        if (!props.isEnabled()) {
            // 默认模拟: 给每个传感器随机生成一条观测
            return mockOnce();
        }
        // TODO 调用 props.getApiUrl() 实际拉取
        log.info("real weather pull (placeholder), api={}", props.getApiUrl());
        return mockOnce();
    }

    private int mockOnce() {
        List<Sensor> sensors = sensorRepository.findAllByDeletedFalse();
        int count = 0;
        for (Sensor s : sensors) {
            String indicator = switch (s.getType()) {
                case "rain_gauge" -> "RAINFALL_HOURLY";
                case "displacement" -> "DISPLACEMENT_24H";
                case "soil_moisture" -> "SOIL_MOISTURE";
                case "weather_station" -> "TEMPERATURE";
                default -> "GENERIC";
            };
            double value = switch (indicator) {
                case "RAINFALL_HOURLY" -> 5 + random.nextDouble() * 60;       // 偶尔触发预警
                case "DISPLACEMENT_24H" -> 1 + random.nextDouble() * 25;
                case "SOIL_MOISTURE" -> 30 + random.nextDouble() * 70;
                case "TEMPERATURE" -> -5 + random.nextDouble() * 35;
                default -> random.nextDouble() * 100;
            };
            CreateObservationDTO dto = new CreateObservationDTO();
            dto.setSensorId(s.getId());
            dto.setIndicator(indicator);
            dto.setValue(Math.round(value * 100.0) / 100.0);
            dto.setUnit(s.getUnit());
            dto.setObservedAt(OffsetDateTime.now());
            try {
                observationService.ingest(dto);
                count++;
            } catch (Exception e) {
                log.warn("ingest failed for sensor {}: {}", s.getCode(), e.getMessage());
            }
        }
        log.info("mock weather pulled {} observations", count);
        return count;
    }

    @Data
    @org.springframework.context.annotation.Configuration
    @ConfigurationProperties(prefix = "gcsj.weather")
    public static class WeatherProps {
        private String apiUrl;
        private String apiKey;
        private boolean enabled = false;
    }
}
