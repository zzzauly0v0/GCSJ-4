package com.gcsj.disaster.service.impl;

import com.gcsj.disaster.common.BusinessException;
import com.gcsj.disaster.common.ErrorCode;
import com.gcsj.disaster.domain.converter.AlertConverter;
import com.gcsj.disaster.domain.dto.CreateAlertDTO;
import com.gcsj.disaster.domain.entity.Alert;
import com.gcsj.disaster.domain.entity.AlertRule;
import com.gcsj.disaster.domain.entity.Observation;
import com.gcsj.disaster.domain.entity.Sensor;
import com.gcsj.disaster.domain.event.AlertTriggeredEvent;
import com.gcsj.disaster.domain.vo.AlertVO;
import com.gcsj.disaster.repository.AlertRepository;
import com.gcsj.disaster.repository.SensorRepository;
import com.gcsj.disaster.service.IAlertService;
import com.gcsj.disaster.utils.GeometryUtil;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements IAlertService {

    private final AlertRepository alertRepository;
    private final SensorRepository sensorRepository;
    private final AlertConverter alertConverter;
    private final ApplicationEventPublisher publisher;

    private static final DateTimeFormatter CODE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Override
    @Transactional
    public AlertVO create(CreateAlertDTO dto) {
        Alert a = new Alert();
        a.setCode(genCode());
        a.setTitle(dto.getTitle());
        a.setContent(dto.getContent());
        a.setLevel(dto.getLevel());
        a.setRuleId(dto.getRuleId());
        a.setSensorId(dto.getSensorId());
        a.setEventId(dto.getEventId());
        if (dto.getLongitude() != null && dto.getLatitude() != null) {
            a.setLocation(GeometryUtil.point(dto.getLongitude(), dto.getLatitude()));
        }
        List<String> channels = dto.getChannels() == null || dto.getChannels().isEmpty()
                ? List.of("in_site") : dto.getChannels();
        a.setChannels(String.join(",", channels));
        a.setStatus((short) 1);
        a.setTriggeredAt(OffsetDateTime.now());
        alertRepository.save(a);
        publisher.publishEvent(new AlertTriggeredEvent(this, a, channels));
        return alertConverter.toVO(a);
    }

    @Override
    @Transactional
    public AlertVO confirm(Long id) {
        Alert a = alertRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ALERT_NOT_FOUND));
        a.setStatus((short) 3);
        a.setConfirmedAt(OffsetDateTime.now());
        alertRepository.save(a);
        return alertConverter.toVO(a);
    }

    @Override
    @Transactional
    public AlertVO close(Long id) {
        Alert a = alertRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ALERT_NOT_FOUND));
        a.setStatus((short) 4);
        alertRepository.save(a);
        return alertConverter.toVO(a);
    }

    @Override
    public AlertVO getById(Long id) {
        return alertConverter.toVO(alertRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ALERT_NOT_FOUND)));
    }

    @Override
    public Page<AlertVO> page(Short level, Short status, Pageable pageable) {
        Specification<Alert> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.isFalse(root.get("deleted")));
            if (level != null) ps.add(cb.equal(root.get("level"), level));
            if (status != null) ps.add(cb.equal(root.get("status"), status));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return alertRepository.findAll(spec, pageable).map(alertConverter::toVO);
    }

    @Override
    public List<AlertVO> latest(int limit) {
        return alertRepository.findTop100ByDeletedFalseOrderByTriggeredAtDesc().stream()
                .limit(limit).map(alertConverter::toVO).toList();
    }

    @Override
    public Map<String, Object> asGeoJson() {
        List<Map<String, Object>> features = new ArrayList<>();
        for (Alert a : alertRepository.findTop100ByDeletedFalseOrderByTriggeredAtDesc()) {
            if (a.getLocation() == null) continue;
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("code", a.getCode());
            props.put("title", a.getTitle());
            props.put("level", a.getLevel());
            props.put("status", a.getStatus());
            props.put("triggeredAt", a.getTriggeredAt());
            features.add(GeometryUtil.toFeature(a.getId(), a.getLocation(), props));
        }
        return GeometryUtil.toFeatureCollection(features);
    }

    @Override
    @Transactional
    public Alert createFromRule(AlertRule rule, Observation observation) {
        Alert a = new Alert();
        a.setCode(genCode());
        a.setTitle("[%s] %s 触发 %s".formatted(rule.getName(), rule.getIndicator(), rule.getThreshold()));
        a.setContent("观测值=%s 阈值=%s 操作符=%s".formatted(observation.getValue(), rule.getThreshold(), rule.getOperator()));
        a.setLevel(rule.getLevel());
        a.setRuleId(rule.getId());
        a.setSensorId(observation.getSensorId());
        Optional<Sensor> sensor = sensorRepository.findById(observation.getSensorId());
        sensor.ifPresent(s -> a.setLocation(s.getLocation()));
        a.setChannels("in_site");
        a.setStatus((short) 1);
        a.setTriggeredAt(OffsetDateTime.now());
        alertRepository.save(a);
        publisher.publishEvent(new AlertTriggeredEvent(this, a, List.of("in_site")));
        return a;
    }

    private String genCode() {
        return "A" + OffsetDateTime.now().format(CODE_FMT)
                + String.format("%04d", new Random().nextInt(10000));
    }
}
