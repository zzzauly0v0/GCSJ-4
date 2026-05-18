package com.gcsj.disaster.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class DisasterEventVO {
    private Long id;
    private String code;
    private String title;
    private String type;
    private Short level;
    private String levelLabel;
    private String levelColor;
    private Double longitude;
    private Double latitude;
    /** 影响区域 GeoJSON, 客户端 OL 直接 read */
    private String affectedAreaGeoJson;
    private OffsetDateTime occurredAt;
    private OffsetDateTime endAt;
    private Short status;
    private String description;
    private Long reporterId;
    private OffsetDateTime createdAt;
}
