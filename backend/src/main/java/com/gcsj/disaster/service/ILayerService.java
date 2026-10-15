package com.gcsj.disaster.service;

import com.gcsj.disaster.domain.dto.CreateLayerDTO;
import com.gcsj.disaster.domain.dto.PublishLayerDTO;
import com.gcsj.disaster.domain.vo.LayerVO;

import java.util.List;

public interface ILayerService {
    LayerVO create(CreateLayerDTO dto);
    LayerVO update(Long id, CreateLayerDTO dto);
    void deleteById(Long id);
    LayerVO getById(Long id);
    List<LayerVO> listAll();

    /**
     * 把图层发布到 GeoServer（当前仅占位，会抛 GEOSERVER_ERROR "未实现"）
     */
    LayerVO publishToGeoServer(Long id, PublishLayerDTO dto);
}
