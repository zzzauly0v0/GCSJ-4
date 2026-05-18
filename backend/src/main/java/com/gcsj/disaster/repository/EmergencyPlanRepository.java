package com.gcsj.disaster.repository;

import com.gcsj.disaster.domain.entity.EmergencyPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmergencyPlanRepository extends JpaRepository<EmergencyPlan, Long> {
    List<EmergencyPlan> findAllByDeletedFalseOrderByIdDesc();
    List<EmergencyPlan> findByDisasterTypeAndEnabledTrue(String disasterType);
}
