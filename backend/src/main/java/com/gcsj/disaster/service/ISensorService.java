package com.gcsj.disaster.service;

import com.gcsj.disaster.domain.dto.CreateSensorDTO;
import com.gcsj.disaster.domain.vo.SensorVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface ISensorService {
    SensorVO create(CreateSensorDTO dto);
    SensorVO update(Long id, CreateSensorDTO dto);
    void deleteById(Long id);
    SensorVO getById(Long id);
    Page<SensorVO> page(String keyword, String type, Pageable pageable);
    List<SensorVO> listAll();
    /** GeoJSON FeatureCollection */
    Map<String, Object> asGeoJson();
}
