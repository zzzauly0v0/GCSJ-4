package com.gcsj.disaster.service.impl;

import com.gcsj.disaster.common.BusinessException;
import com.gcsj.disaster.common.ErrorCode;
import com.gcsj.disaster.domain.converter.AlertRuleConverter;
import com.gcsj.disaster.domain.dto.CreateAlertRuleDTO;
import com.gcsj.disaster.domain.entity.AlertRule;
import com.gcsj.disaster.domain.vo.AlertRuleVO;
import com.gcsj.disaster.repository.AlertRuleRepository;
import com.gcsj.disaster.service.IAlertRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AlertRuleServiceImpl implements IAlertRuleService {

    private final AlertRuleRepository alertRuleRepository;
    private final AlertRuleConverter alertRuleConverter;

    @Override
    @Transactional
    public AlertRuleVO create(CreateAlertRuleDTO dto) {
        AlertRule r = alertRuleConverter.fromCreate(dto);
        alertRuleRepository.save(r);
        return alertRuleConverter.toVO(r);
    }

    @Override
    @Transactional
    public AlertRuleVO update(Long id, CreateAlertRuleDTO dto) {
        AlertRule r = alertRuleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ALERT_RULE_NOT_FOUND));
        r.setName(dto.getName());
        r.setIndicator(dto.getIndicator());
        r.setOperator(dto.getOperator());
        r.setThreshold(dto.getThreshold());
        r.setLevel(dto.getLevel());
        r.setDisasterType(dto.getDisasterType());
        r.setEnabled(dto.getEnabled() == null ? Boolean.TRUE : dto.getEnabled());
        r.setDescription(dto.getDescription());
        alertRuleRepository.save(r);
        return alertRuleConverter.toVO(r);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        AlertRule r = alertRuleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ALERT_RULE_NOT_FOUND));
        r.setDeleted(true);
        alertRuleRepository.save(r);
    }

    @Override
    public List<AlertRuleVO> listAll() {
        return alertRuleRepository.findAll().stream()
                .filter(r -> !Boolean.TRUE.equals(r.getDeleted()))
                .map(alertRuleConverter::toVO).toList();
    }

    @Override
    public AlertRule evaluate(String indicator, double value) {
        List<AlertRule> rules = alertRuleRepository.findByIndicatorAndEnabledTrueAndDeletedFalse(indicator);
        return rules.stream()
                .filter(r -> compare(value, r.getOperator(), r.getThreshold()))
                .max(Comparator.comparing(AlertRule::getLevel))   // 取最高级
                .orElse(null);
    }

    private boolean compare(double value, String op, double threshold) {
        return switch (op) {
            case ">" -> value > threshold;
            case ">=" -> value >= threshold;
            case "<" -> value < threshold;
            case "<=" -> value <= threshold;
            case "==" -> value == threshold;
            default -> false;
        };
    }
}
