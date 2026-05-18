package com.gcsj.disaster.service.impl;

import com.gcsj.disaster.common.BusinessException;
import com.gcsj.disaster.common.ErrorCode;
import com.gcsj.disaster.domain.converter.SensorConverter;
import com.gcsj.disaster.domain.dto.CreateSensorDTO;
import com.gcsj.disaster.domain.entity.Sensor;
import com.gcsj.disaster.domain.vo.SensorVO;
import com.gcsj.disaster.repository.SensorRepository;
import com.gcsj.disaster.service.ISensorService;
import com.gcsj.disaster.utils.GeometryUtil;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class SensorServiceImpl implements ISensorService {

    private final SensorRepository sensorRepository;
    private final SensorConverter sensorConverter;

    @Override
    @Transactional
    public SensorVO create(CreateSensorDTO dto) {
        if (sensorRepository.existsByCode(dto.getCode())) {
            throw new BusinessException(ErrorCode.SENSOR_CODE_DUPLICATE);
        }
        Sensor s = sensorConverter.fromCreate(dto);
        sensorRepository.save(s);
        return sensorConverter.toVO(s);
    }

    @Override
    @Transactional
    public SensorVO update(Long id, CreateSensorDTO dto) {
        Sensor s = sensorRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SENSOR_NOT_FOUND));
        s.setName(dto.getName());
        s.setType(dto.getType());
        s.setOrgId(dto.getOrgId());
        s.setLocation(GeometryUtil.point(dto.getLongitude(), dto.getLatitude()));
        s.setElevation(dto.getElevation());
        s.setUnit(dto.getUnit());
        s.setDescription(dto.getDescription());
        sensorRepository.save(s);
        return sensorConverter.toVO(s);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        Sensor s = sensorRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SENSOR_NOT_FOUND));
        s.setDeleted(true);
        sensorRepository.save(s);
    }

    @Override
    public SensorVO getById(Long id) {
        return sensorConverter.toVO(sensorRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SENSOR_NOT_FOUND)));
    }

    @Override
    public Page<SensorVO> page(String keyword, String type, Pageable pageable) {
        Specification<Sensor> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.isFalse(root.get("deleted")));
            if (type != null && !type.isBlank()) ps.add(cb.equal(root.get("type"), type));
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword + "%";
                ps.add(cb.or(cb.like(root.get("code"), like), cb.like(root.get("name"), like)));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return sensorRepository.findAll(spec, pageable).map(sensorConverter::toVO);
    }

    @Override
    public List<SensorVO> listAll() {
        return sensorRepository.findAllByDeletedFalse().stream().map(sensorConverter::toVO).toList();
    }

    @Override
    public Map<String, Object> asGeoJson() {
        List<Map<String, Object>> features = new ArrayList<>();
        for (Sensor s : sensorRepository.findAllByDeletedFalse()) {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("code", s.getCode());
            props.put("name", s.getName());
            props.put("type", s.getType());
            props.put("status", s.getStatus());
            props.put("unit", s.getUnit());
            features.add(GeometryUtil.toFeature(s.getId(), s.getLocation(), props));
        }
        return GeometryUtil.toFeatureCollection(features);
    }
}
