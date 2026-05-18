package com.gcsj.disaster.service;

import com.gcsj.disaster.domain.dto.CreateEmergencyPlanDTO;
import com.gcsj.disaster.domain.vo.EmergencyPlanVO;

import java.util.List;

public interface IEmergencyPlanService {
    EmergencyPlanVO create(CreateEmergencyPlanDTO dto);
    EmergencyPlanVO update(Long id, CreateEmergencyPlanDTO dto);
    void deleteById(Long id);
    EmergencyPlanVO getById(Long id);
    List<EmergencyPlanVO> listAll();
    List<EmergencyPlanVO> findApplicable(String disasterType, Short level);
}
