package com.gcsj.disaster.repository;

import com.gcsj.disaster.domain.entity.AdminRegion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminRegionRepository extends JpaRepository<AdminRegion, Long> {

    List<AdminRegion> findAllByLevelOrderByAdcode(Short level);

    List<AdminRegion> findAllByLevelAndParentCodeOrderByAdcode(Short level, String parentCode);

    Optional<AdminRegion> findByAdcode(String adcode);
}
