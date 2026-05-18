package com.gcsj.disaster.repository;

import com.gcsj.disaster.domain.entity.DisasterEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface DisasterEventRepository extends JpaRepository<DisasterEvent, Long>, JpaSpecificationExecutor<DisasterEvent> {
    List<DisasterEvent> findTop50ByDeletedFalseOrderByOccurredAtDesc();
}
