package com.gcsj.disaster.domain.converter;

import com.gcsj.disaster.common.AlertLevel;
import com.gcsj.disaster.domain.entity.Alert;
import com.gcsj.disaster.domain.vo.AlertVO;
import com.gcsj.disaster.repository.DisasterEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AlertConverter {

    private final DisasterEventRepository disasterEventRepository;

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
        vo.setEventId(a.getEventId());
        if (a.getEventId() != null) {
            disasterEventRepository.findById(a.getEventId())
                    .ifPresent(e -> vo.setEventCode(e.getCode()));
        }
        vo.setSource(a.getSource());
        if (a.getLocation() != null) {
            vo.setLongitude(a.getLocation().getX());
            vo.setLatitude(a.getLocation().getY());
        }
        vo.setChannels(splitChannels(a.getChannels()));
        vo.setStatus(a.getStatus());
        vo.setTriggeredAt(a.getTriggeredAt());
        vo.setSentAt(a.getSentAt());
        vo.setConfirmedAt(a.getConfirmedAt());
        vo.setConfirmedById(a.getConfirmedById());
        return vo;
    }

    private List<String> splitChannels(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
