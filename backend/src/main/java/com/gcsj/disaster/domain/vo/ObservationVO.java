package com.gcsj.disaster.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class ObservationVO {
    private Long id;
    private Long sensorId;
    private String sensorName;
    private String indicator;
    private Double value;
    private String unit;
    private OffsetDateTime observedAt;
    private Boolean abnormal;
}
