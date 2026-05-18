package com.gcsj.disaster.repository;

import com.gcsj.disaster.domain.entity.Sensor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface SensorRepository extends JpaRepository<Sensor, Long>, JpaSpecificationExecutor<Sensor> {
    List<Sensor> findAllByDeletedFalse();
    boolean existsByCode(String code);
}
