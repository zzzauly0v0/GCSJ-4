package com.gcsj.disaster.domain.converter;

import com.gcsj.disaster.common.AlertLevel;
import com.gcsj.disaster.domain.dto.CreateAlertRuleDTO;
import com.gcsj.disaster.domain.entity.AlertRule;
import com.gcsj.disaster.domain.vo.AlertRuleVO;
import org.springframework.stereotype.Component;

@Component
public class AlertRuleConverter {

    public AlertRuleVO toVO(AlertRule r) {
        if (r == null) return null;
        AlertRuleVO vo = new AlertRuleVO();
        vo.setId(r.getId());
        vo.setName(r.getName());
        vo.setIndicator(r.getIndicator());
        vo.setOperator(r.getOperator());
        vo.setThreshold(r.getThreshold());
        vo.setLevel(r.getLevel());
        if (r.getLevel() != null) vo.setLevelLabel(AlertLevel.of(r.getLevel()).getLabel());
        vo.setDisasterType(r.getDisasterType());
        vo.setEnabled(r.getEnabled());
        vo.setDescription(r.getDescription());
        return vo;
    }

    public AlertRule fromCreate(CreateAlertRuleDTO dto) {
        AlertRule r = new AlertRule();
        r.setName(dto.getName());
        r.setIndicator(dto.getIndicator());
        r.setOperator(dto.getOperator());
        r.setThreshold(dto.getThreshold());
        r.setLevel(dto.getLevel());
        r.setDisasterType(dto.getDisasterType());
        r.setEnabled(dto.getEnabled() == null ? Boolean.TRUE : dto.getEnabled());
        r.setDescription(dto.getDescription());
        return r;
    }
}
