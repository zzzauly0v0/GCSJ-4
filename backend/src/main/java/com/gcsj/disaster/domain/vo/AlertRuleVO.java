package com.gcsj.disaster.domain.vo;

import lombok.Data;

@Data
public class AlertRuleVO {
    private Long id;
    private String name;
    private String indicator;
    private String operator;
    private Double threshold;
    private Short level;
    private String levelLabel;
    private String disasterType;
    private Boolean enabled;
    private String description;
}
