package com.gcsj.disaster.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "biz_emergency_plan")
public class EmergencyPlan extends BaseEntity {
    @Column(unique = true, nullable = false, length = 64)
    private String code;
    @Column(nullable = false, length = 256)
    private String name;
    @Column(name = "disaster_type", length = 32)
    private String disasterType;
    private Short level;
    @Column(columnDefinition = "text")
    private String content;
    @Column(name = "file_url", length = 512)
    private String fileUrl;
    @Column(nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
