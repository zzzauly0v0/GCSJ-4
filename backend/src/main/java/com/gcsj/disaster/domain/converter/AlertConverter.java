package com.gcsj.disaster.domain.converter;

import com.gcsj.disaster.common.AlertLevel;
import com.gcsj.disaster.domain.entity.Alert;
import com.gcsj.disaster.domain.vo.AlertVO;
import org.springframework.stereotype.Component;

@Component
public class AlertConverter {

    public AlertVO toVO(Alert a) {
        if (a == null) return null;
        AlertVO vo = new AlertVO();
        vo.setId(a.getId());
        vo.setCode(a.getCode());
        vo.setTitle(a.getTitle());
        vo.setContent(a.getContent());
        vo.setLevel(a.getLevel());
        if (a.getLevel() != null) {
            AlertLevel lv = AlertLevel.of(a.getLevel());
            vo.setLevelLabel(lv.getLabel());
            vo.setLevelColor(lv.getColor());
        }
        vo.setRuleId(a.getRuleId());
        vo.setSensorId(a.getSensorId());
        vo.setEventId(a.getEventId());
        if (a.getLocation() != null) {
            vo.setLongitude(a.getLocation().getX());
            vo.setLatitude(a.getLocation().getY());
        }
        vo.setChannels(a.getChannels());
        vo.setStatus(a.getStatus());
        vo.setTriggeredAt(a.getTriggeredAt());
        vo.setSentAt(a.getSentAt());
        vo.setConfirmedAt(a.getConfirmedAt());
        return vo;
    }
}
