package com.gcsj.disaster.service;

import com.gcsj.disaster.domain.dto.CreateLayerDTO;
import com.gcsj.disaster.domain.vo.LayerVO;

import java.util.List;

public interface ILayerService {
    LayerVO create(CreateLayerDTO dto);
    LayerVO update(Long id, CreateLayerDTO dto);
    void deleteById(Long id);
    LayerVO getById(Long id);
    List<LayerVO> listAll();
}
