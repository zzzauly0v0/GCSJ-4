package com.gcsj.disaster.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "biz_alert_rule")
public class AlertRule extends BaseEntity {
    @Column(nullable = false, length = 128)
    private String name;
    @Column(nullable = false, length = 32)
    private String indicator;
    @Column(nullable = false, length = 8)
    private String operator;
    @Column(nullable = false)
    private Double threshold;
    @Column(nullable = false)
    private Short level;
    @Column(name = "disaster_type", length = 32)
    private String disasterType;
    @Column(nullable = false)
    private Boolean enabled = Boolean.TRUE;
    @Column(length = 256)
    private String description;
}
