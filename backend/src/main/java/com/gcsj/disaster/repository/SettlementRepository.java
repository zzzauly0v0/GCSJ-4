package com.gcsj.disaster.repository;

import com.gcsj.disaster.domain.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {
    List<Settlement> findAllByOrderByIdAsc();
    List<Settlement> findAllByTypeOrderByIdAsc(String type);
}
