package com.gcsj.disaster.domain.vo;

import lombok.Data;

@Data
public class EmergencyPlanVO {
    private Long id;
    private String code;
    private String name;
    private String disasterType;
    private Short level;
    private String content;
    private String fileUrl;
    private Boolean enabled;
}
