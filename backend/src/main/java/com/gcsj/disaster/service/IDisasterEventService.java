package com.gcsj.disaster.service;

import com.gcsj.disaster.domain.dto.CreateDisasterEventDTO;
import com.gcsj.disaster.domain.vo.DisasterEventVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface IDisasterEventService {
    DisasterEventVO create(CreateDisasterEventDTO dto);
    DisasterEventVO update(Long id, CreateDisasterEventDTO dto);
    void deleteById(Long id);
    DisasterEventVO getById(Long id);
    Page<DisasterEventVO> page(Short level, String type, Pageable pageable);
    List<DisasterEventVO> latest(int limit);
    Map<String, Object> asGeoJson();
}
