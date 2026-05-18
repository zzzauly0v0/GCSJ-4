package com.gcsj.disaster.service.impl;

import com.gcsj.disaster.common.BusinessException;
import com.gcsj.disaster.common.ErrorCode;
import com.gcsj.disaster.domain.converter.ObservationConverter;
import com.gcsj.disaster.domain.dto.CreateObservationDTO;
import com.gcsj.disaster.domain.entity.Observation;
import com.gcsj.disaster.domain.event.ObservationReceivedEvent;
import com.gcsj.disaster.domain.vo.ObservationVO;
import com.gcsj.disaster.repository.ObservationRepository;
import com.gcsj.disaster.repository.SensorRepository;
import com.gcsj.disaster.service.IObservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ObservationServiceImpl implements IObservationService {

    private final ObservationRepository observationRepository;
    private final SensorRepository sensorRepository;
    private final ObservationConverter observationConverter;
    private final ApplicationEventPublisher publisher;

    @Override
    @Transactional
    public ObservationVO ingest(CreateObservationDTO dto) {
        if (!sensorRepository.existsById(dto.getSensorId())) {
            throw new BusinessException(ErrorCode.SENSOR_NOT_FOUND);
        }
        Observation o = new Observation();
        o.setSensorId(dto.getSensorId());
        o.setIndicator(dto.getIndicator());
        o.setValue(dto.getValue());
        o.setUnit(dto.getUnit());
        o.setObservedAt(dto.getObservedAt() == null ? OffsetDateTime.now() : dto.getObservedAt());
        o.setAbnormal(false);
        observationRepository.save(o);
        // 发事件 -> 规则引擎评估
        publisher.publishEvent(new ObservationReceivedEvent(this, o));
        return observationConverter.toVO(o);
    }

    @Override
    public List<ObservationVO> series(Long sensorId, String indicator, OffsetDateTime from, OffsetDateTime to) {
        if (from == null) from = OffsetDateTime.now().minusDays(1);
        if (to == null) to = OffsetDateTime.now();
        return observationRepository.findBySensorIdAndObservedAtBetweenOrderByObservedAtAsc(sensorId, from, to)
                .stream().filter(o -> indicator == null || indicator.equals(o.getIndicator()))
                .map(observationConverter::toVO).toList();
    }

    @Override
    public Page<ObservationVO> page(Long sensorId, Pageable pageable) {
        Page<Observation> p = sensorId == null
                ? observationRepository.findAll(pageable)
                : observationRepository.findBySensorId(sensorId, pageable);
        return p.map(observationConverter::toVO);
    }

    @Override
    public List<ObservationVO> latest(int limit) {
        Pageable pg = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "observedAt"));
        return observationRepository.findAll(pg).stream().map(observationConverter::toVO).toList();
    }
}
