package com.gcsj.disaster.repository;

import com.gcsj.disaster.domain.entity.Dictionary;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DictionaryRepository extends JpaRepository<Dictionary, Long> {
    List<Dictionary> findByTypeCodeOrderBySortAsc(String typeCode);
}
