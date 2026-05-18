package com.gcsj.disaster.domain.converter;

import com.gcsj.disaster.domain.dto.CreateLayerDTO;
import com.gcsj.disaster.domain.entity.Layer;
import com.gcsj.disaster.domain.vo.LayerVO;
import org.springframework.stereotype.Component;

@Component
public class LayerConverter {
    public LayerVO toVO(Layer l) {
        if (l == null) return null;
        LayerVO vo = new LayerVO();
        vo.setId(l.getId());
        vo.setName(l.getName());
        vo.setCode(l.getCode());
        vo.setType(l.getType());
        vo.setSourceUrl(l.getSourceUrl());
        vo.setWorkspace(l.getWorkspace());
        vo.setLayerName(l.getLayerName());
        vo.setStyle(l.getStyle());
        vo.setVisible(l.getVisible());
        vo.setZIndex(l.getZIndex());
        vo.setDescription(l.getDescription());
        return vo;
    }
    public Layer fromCreate(CreateLayerDTO dto) {
        Layer l = new Layer();
        l.setName(dto.getName());
        l.setCode(dto.getCode());
        l.setType(dto.getType());
        l.setSourceUrl(dto.getSourceUrl());
        l.setWorkspace(dto.getWorkspace());
        l.setLayerName(dto.getLayerName());
        l.setStyle(dto.getStyle());
        l.setVisible(dto.getVisible() == null ? Boolean.TRUE : dto.getVisible());
        l.setZIndex(dto.getZIndex() == null ? 0 : dto.getZIndex());
        l.setDescription(dto.getDescription());
        return l;
    }
}
