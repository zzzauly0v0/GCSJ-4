package com.gcsj.disaster.service.impl;

import com.gcsj.disaster.common.BusinessException;
import com.gcsj.disaster.common.ErrorCode;
import com.gcsj.disaster.domain.converter.LayerConverter;
import com.gcsj.disaster.domain.dto.CreateLayerDTO;
import com.gcsj.disaster.domain.entity.Layer;
import com.gcsj.disaster.domain.vo.LayerVO;
import com.gcsj.disaster.repository.LayerRepository;
import com.gcsj.disaster.service.ILayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LayerServiceImpl implements ILayerService {

    private final LayerRepository layerRepository;
    private final LayerConverter layerConverter;

    @Override
    @Transactional
    public LayerVO create(CreateLayerDTO dto) {
        if (layerRepository.existsByCode(dto.getCode())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "图层编码重复");
        }
        Layer l = layerConverter.fromCreate(dto);
        layerRepository.save(l);
        return layerConverter.toVO(l);
    }

    @Override
    @Transactional
    public LayerVO update(Long id, CreateLayerDTO dto) {
        Layer l = layerRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.LAYER_NOT_FOUND));
        l.setName(dto.getName());
        l.setType(dto.getType());
        l.setSourceUrl(dto.getSourceUrl());
        l.setWorkspace(dto.getWorkspace());
        l.setLayerName(dto.getLayerName());
        l.setStyle(dto.getStyle());
        if (dto.getVisible() != null) l.setVisible(dto.getVisible());
        if (dto.getZIndex() != null) l.setZIndex(dto.getZIndex());
        l.setDescription(dto.getDescription());
        layerRepository.save(l);
        return layerConverter.toVO(l);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        Layer l = layerRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.LAYER_NOT_FOUND));
        l.setDeleted(true);
        layerRepository.save(l);
    }

    @Override
    public LayerVO getById(Long id) {
        return layerConverter.toVO(layerRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.LAYER_NOT_FOUND)));
    }

    @Override
    public List<LayerVO> listAll() {
        return layerRepository.findAllVisibleOrdered().stream()
                .map(layerConverter::toVO).toList();
    }
}
