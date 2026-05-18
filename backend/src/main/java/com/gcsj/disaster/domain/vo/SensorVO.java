package com.gcsj.disaster.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class SensorVO {
    private Long id;
    private String code;
    private String name;
    private String type;
    private Long orgId;
    private Double longitude;
    private Double latitude;
    private Double elevation;
    private String unit;
    private OffsetDateTime installAt;
    private Short status;
    private String description;
}
