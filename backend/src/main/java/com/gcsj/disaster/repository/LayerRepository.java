package com.gcsj.disaster.repository;

import com.gcsj.disaster.domain.entity.Layer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LayerRepository extends JpaRepository<Layer, Long> {
    @Query("select l from Layer l where l.deleted = false order by l.zIndex asc")
    List<Layer> findAllVisibleOrdered();
    boolean existsByCode(String code);
}
