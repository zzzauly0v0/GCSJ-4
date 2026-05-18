package com.gcsj.disaster.repository;

import com.gcsj.disaster.domain.entity.Observation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.OffsetDateTime;
import java.util.List;

public interface ObservationRepository extends JpaRepository<Observation, Long>, JpaSpecificationExecutor<Observation> {
    List<Observation> findBySensorIdAndObservedAtBetweenOrderByObservedAtAsc(
            Long sensorId, OffsetDateTime from, OffsetDateTime to);

    Page<Observation> findBySensorId(Long sensorId, Pageable pageable);
}
