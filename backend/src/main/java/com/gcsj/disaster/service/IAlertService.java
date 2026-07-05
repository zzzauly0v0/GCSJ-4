package com.gcsj.disaster.service;

import com.gcsj.disaster.domain.dto.CreateAlertDTO;
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
    Page<AlertVO> page(String keyword, Short level, Short status, Long eventId, Pageable pageable);
    List<AlertVO> latest(int limit);
    Map<String, Object> asGeoJson();

    /** 由灾害事件挂起预警: 从一条已有事件直接生成预警 (source=event) */
    AlertVO createFromEvent(Long eventId, Short level, String title, String content, List<String> channels);

    /**
     * 从风险研判结果批量生成预警 (source=eval)。
     * 扫描 biz_disaster_eval 中 comp_level>=minLevel 的风险日, 按 (站点+日期) 去重后落库。
     * 反复调用幂等。返回 { scanned, created, skipped }。
     */
    Map<String, Object> generateFromEval(Integer year, int minLevel);

    /** 预警统计摘要：total / level1~4 / done（已确认+已关闭） */
    Map<String, Object> stats();
}
