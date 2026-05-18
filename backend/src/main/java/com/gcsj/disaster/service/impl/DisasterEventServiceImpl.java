package com.gcsj.disaster.service.impl;

import com.gcsj.disaster.common.BusinessException;
import com.gcsj.disaster.common.ErrorCode;
import com.gcsj.disaster.domain.converter.DisasterEventConverter;
import com.gcsj.disaster.domain.dto.CreateDisasterEventDTO;
import com.gcsj.disaster.domain.entity.DisasterEvent;
import com.gcsj.disaster.domain.vo.DisasterEventVO;
import com.gcsj.disaster.repository.DisasterEventRepository;
import com.gcsj.disaster.service.IDisasterEventService;
import com.gcsj.disaster.utils.GeometryUtil;
import com.gcsj.disaster.utils.SecurityContextUtil;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DisasterEventServiceImpl implements IDisasterEventService {

    private final DisasterEventRepository repository;
    private final DisasterEventConverter converter;
    private final SimpMessagingTemplate messaging;

    private static final DateTimeFormatter CODE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    @Transactional
    public DisasterEventVO create(CreateDisasterEventDTO dto) {
        DisasterEvent d = converter.fromCreate(dto);
        d.setCode(genCode());
        SecurityContextUtil.AuthUser u = SecurityContextUtil.currentOrNull();
        if (u != null) d.setReporterId(u.userId());
        repository.save(d);
        DisasterEventVO vo = converter.toVO(d);
        messaging.convertAndSend("/topic/disasters", vo);
        return vo;
    }

    @Override
    @Transactional
    public DisasterEventVO update(Long id, CreateDisasterEventDTO dto) {
        DisasterEvent d = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.DISASTER_EVENT_NOT_FOUND));
        d.setTitle(dto.getTitle());
        d.setType(dto.getType());
        d.setLevel(dto.getLevel());
        d.setLocation(GeometryUtil.point(dto.getLongitude(), dto.getLatitude()));
        if (dto.getAffectedAreaGeoJson() != null && !dto.getAffectedAreaGeoJson().isBlank()) {
            d.setAffectedArea((org.locationtech.jts.geom.Polygon) GeometryUtil.fromGeoJson(dto.getAffectedAreaGeoJson()));
        }
        d.setOccurredAt(dto.getOccurredAt());
        d.setDescription(dto.getDescription());
        repository.save(d);
        return converter.toVO(d);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        DisasterEvent d = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.DISASTER_EVENT_NOT_FOUND));
        d.setDeleted(true);
        repository.save(d);
    }

    @Override
    public DisasterEventVO getById(Long id) {
        return converter.toVO(repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.DISASTER_EVENT_NOT_FOUND)));
    }

    @Override
    public Page<DisasterEventVO> page(Short level, String type, Pageable pageable) {
        Specification<DisasterEvent> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.isFalse(root.get("deleted")));
            if (level != null) ps.add(cb.equal(root.get("level"), level));
            if (type != null && !type.isBlank()) ps.add(cb.equal(root.get("type"), type));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return repository.findAll(spec, pageable).map(converter::toVO);
    }

    @Override
    public List<DisasterEventVO> latest(int limit) {
        return repository.findTop50ByDeletedFalseOrderByOccurredAtDesc().stream()
                .limit(limit).map(converter::toVO).toList();
    }

    @Override
    public Map<String, Object> asGeoJson() {
        List<Map<String, Object>> features = new ArrayList<>();
        for (DisasterEvent d : repository.findTop50ByDeletedFalseOrderByOccurredAtDesc()) {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("code", d.getCode());
            props.put("title", d.getTitle());
            props.put("type", d.getType());
            props.put("level", d.getLevel());
            props.put("status", d.getStatus());
            props.put("occurredAt", d.getOccurredAt());
            features.add(GeometryUtil.toFeature(d.getId(), d.getLocation(), props));
        }
        return GeometryUtil.toFeatureCollection(features);
    }

    private String genCode() {
        return "E" + OffsetDateTime.now().format(CODE_FMT)
                + String.format("%05d", new Random().nextInt(100000));
    }
}
