package com.gcsj.disaster.service;

import com.gcsj.disaster.domain.dto.CreateAlertRuleDTO;
import com.gcsj.disaster.domain.entity.AlertRule;
import com.gcsj.disaster.domain.vo.AlertRuleVO;
import java.util.List;

public interface IAlertRuleService {
    AlertRuleVO create(CreateAlertRuleDTO dto);
    AlertRuleVO update(Long id, CreateAlertRuleDTO dto);
    void deleteById(Long id);
    List<AlertRuleVO> listAll();
    /** 评估指标 / 取值, 返回触发的最高级规则 (null = 未触发) */
    AlertRule evaluate(String indicator, double value);
}
