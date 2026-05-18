package com.gcsj.disaster.repository;

import com.gcsj.disaster.domain.entity.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {
    List<AlertRule> findAllByEnabledTrueAndDeletedFalse();
    List<AlertRule> findByIndicatorAndEnabledTrueAndDeletedFalse(String indicator);
}
