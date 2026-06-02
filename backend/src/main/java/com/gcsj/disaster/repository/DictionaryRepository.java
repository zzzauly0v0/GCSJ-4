package com.gcsj.disaster.repository;

import com.gcsj.disaster.domain.entity.Dictionary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DictionaryRepository extends JpaRepository<Dictionary, Long> {
    List<Dictionary> findByTypeCodeOrderBySortAsc(String typeCode);

    List<Dictionary> findAllByOrderByTypeCodeAscSortAsc();

    Optional<Dictionary> findByTypeCodeAndItemCode(String typeCode, String itemCode);

    @Query("SELECT DISTINCT d.typeCode FROM Dictionary d ORDER BY d.typeCode")
    List<String> findDistinctTypeCodes();
}
