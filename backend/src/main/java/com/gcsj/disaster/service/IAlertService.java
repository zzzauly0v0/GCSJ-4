package com.gcsj.disaster.service;

import com.gcsj.disaster.domain.dto.CreateAlertDTO;
import com.gcsj.disaster.domain.entity.Alert;
import com.gcsj.disaster.domain.entity.AlertRule;
import com.gcsj.disaster.domain.entity.Observation;
import com.gcsj.disaster.domain.vo.AlertVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface IAlertService {
    AlertVO create(CreateAlertDTO dto);
    AlertVO confirm(Long id);
    AlertVO close(Long id);
    AlertVO getById(Long id);
    Page<AlertVO> page(Short level, Short status, Pageable pageable);
    List<AlertVO> latest(int limit);
    Map<String, Object> asGeoJson();

    /** 由规则引擎调用: 自动从观测数据触发预警 */
    Alert createFromRule(AlertRule rule, Observation observation);
}
