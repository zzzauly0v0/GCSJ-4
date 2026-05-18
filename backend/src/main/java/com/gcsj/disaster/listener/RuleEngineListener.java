package com.gcsj.disaster.listener;

import com.gcsj.disaster.domain.entity.AlertRule;
import com.gcsj.disaster.domain.entity.Observation;
import com.gcsj.disaster.domain.event.ObservationReceivedEvent;
import com.gcsj.disaster.repository.ObservationRepository;
import com.gcsj.disaster.service.IAlertRuleService;
import com.gcsj.disaster.service.IAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 规则引擎监听器 - 观察者模式
 * 观测数据入库后, 评估预警规则, 命中则创建预警事件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleEngineListener {

    private final IAlertRuleService alertRuleService;
    private final IAlertService alertService;
    private final ObservationRepository observationRepository;
    private final SimpMessagingTemplate messaging;

    @EventListener
    public void onObservation(ObservationReceivedEvent event) {
        Observation obs = event.getObservation();
        try {
            AlertRule hit = alertRuleService.evaluate(obs.getIndicator(), obs.getValue());
            if (hit != null) {
                obs.setAbnormal(true);
                observationRepository.save(obs);
                alertService.createFromRule(hit, obs);
            }
            // 推送最新观测 (限频可在前端做)
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sensorId", obs.getSensorId());
            payload.put("indicator", obs.getIndicator());
            payload.put("value", obs.getValue());
            payload.put("observedAt", obs.getObservedAt());
            payload.put("abnormal", obs.getAbnormal());
            messaging.convertAndSend("/topic/observations", payload);
        } catch (Exception e) {
            log.error("rule engine eval failed", e);
        }
    }
}
