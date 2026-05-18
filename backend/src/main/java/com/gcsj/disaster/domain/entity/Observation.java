package com.gcsj.disaster.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

@Data
@Entity
@Table(name = "biz_observation")
@EntityListeners(AuditingEntityListener.class)
public class Observation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "sensor_id", nullable = false)
    private Long sensorId;
    @Column(nullable = false, length = 32)
    private String indicator;
    @Column(nullable = false)
    private Double value;
    @Column(length = 16)
    private String unit;
    @Column(name = "observed_at", nullable = false)
    private OffsetDateTime observedAt;
    @Column(nullable = false)
    private Boolean abnormal = Boolean.FALSE;
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
