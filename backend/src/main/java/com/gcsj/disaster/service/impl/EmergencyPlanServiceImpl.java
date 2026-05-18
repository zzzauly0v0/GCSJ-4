package com.gcsj.disaster.service.impl;

import com.gcsj.disaster.common.BusinessException;
import com.gcsj.disaster.common.ErrorCode;
import com.gcsj.disaster.domain.dto.CreateEmergencyPlanDTO;
import com.gcsj.disaster.domain.entity.EmergencyPlan;
import com.gcsj.disaster.domain.vo.EmergencyPlanVO;
import com.gcsj.disaster.repository.EmergencyPlanRepository;
import com.gcsj.disaster.service.IEmergencyPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmergencyPlanServiceImpl implements IEmergencyPlanService {

    private final EmergencyPlanRepository repository;

    @Override
    @Transactional
    public EmergencyPlanVO create(CreateEmergencyPlanDTO dto) {
        EmergencyPlan p = new EmergencyPlan();
        p.setCode(dto.getCode());
        p.setName(dto.getName());
        p.setDisasterType(dto.getDisasterType());
        p.setLevel(dto.getLevel());
        p.setContent(dto.getContent());
        p.setFileUrl(dto.getFileUrl());
        p.setEnabled(dto.getEnabled() == null ? Boolean.TRUE : dto.getEnabled());
        repository.save(p);
        return toVO(p);
    }

    @Override
    @Transactional
    public EmergencyPlanVO update(Long id, CreateEmergencyPlanDTO dto) {
        EmergencyPlan p = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "应急预案不存在"));
        p.setName(dto.getName());
        p.setDisasterType(dto.getDisasterType());
        p.setLevel(dto.getLevel());
        p.setContent(dto.getContent());
        p.setFileUrl(dto.getFileUrl());
        if (dto.getEnabled() != null) p.setEnabled(dto.getEnabled());
        repository.save(p);
        return toVO(p);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        EmergencyPlan p = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        p.setDeleted(true);
        repository.save(p);
    }

    @Override
    public EmergencyPlanVO getById(Long id) {
        return toVO(repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND)));
    }

    @Override
    public List<EmergencyPlanVO> listAll() {
        return repository.findAllByDeletedFalseOrderByIdDesc().stream().map(this::toVO).toList();
    }

    @Override
    public List<EmergencyPlanVO> findApplicable(String disasterType, Short level) {
        return repository.findByDisasterTypeAndEnabledTrue(disasterType).stream()
                .filter(p -> level == null || p.getLevel() == null || p.getLevel() <= level)
                .map(this::toVO).toList();
    }

    private EmergencyPlanVO toVO(EmergencyPlan p) {
        EmergencyPlanVO vo = new EmergencyPlanVO();
        vo.setId(p.getId());
        vo.setCode(p.getCode());
        vo.setName(p.getName());
        vo.setDisasterType(p.getDisasterType());
        vo.setLevel(p.getLevel());
        vo.setContent(p.getContent());
        vo.setFileUrl(p.getFileUrl());
        vo.setEnabled(p.getEnabled());
        return vo;
    }
}
