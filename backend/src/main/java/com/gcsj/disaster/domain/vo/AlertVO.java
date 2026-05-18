package com.gcsj.disaster.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class AlertVO {
    private Long id;
    private String code;
    private String title;
    private String content;
    private Short level;
    private String levelLabel;
    private String levelColor;
    private Long ruleId;
    private Long sensorId;
    private Long eventId;
    private Double longitude;
    private Double latitude;
    private String channels;
    private Short status;
    private OffsetDateTime triggeredAt;
    private OffsetDateTime sentAt;
    private OffsetDateTime confirmedAt;
}
