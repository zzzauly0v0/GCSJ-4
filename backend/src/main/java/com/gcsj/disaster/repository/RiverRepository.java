package com.gcsj.disaster.repository;

import com.gcsj.disaster.domain.entity.River;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RiverRepository extends JpaRepository<River, Long> {
    List<River> findAllByOrderByGradeAscIdAsc();
}
