package com.gcsj.disaster.repository;

import com.gcsj.disaster.domain.entity.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long>, JpaSpecificationExecutor<Alert> {
    List<Alert> findTop100ByDeletedFalseOrderByTriggeredAtDesc();
    Page<Alert> findAllByDeletedFalse(Pageable pageable);
}
