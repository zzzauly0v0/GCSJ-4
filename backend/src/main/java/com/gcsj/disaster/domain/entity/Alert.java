package com.gcsj.disaster.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.locationtech.jts.geom.Point;

import java.time.OffsetDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "biz_alert")
public class Alert extends BaseEntity {
    @Column(unique = true, nullable = false, length = 64)
    private String code;
    @Column(nullable = false, length = 256)
    private String title;
    @Column(columnDefinition = "text")
    private String content;
    @Column(nullable = false)
    private Short level;
    @Column(name = "rule_id")
    private Long ruleId;
    @Column(name = "sensor_id")
    private Long sensorId;
    @Column(name = "event_id")
    private Long eventId;
    @Column(columnDefinition = "geometry(Point, 4326)")
    private Point location;
    @Column(length = 128)
    private String channels;        // comma-separated
    @Column(nullable = false)
    private Short status = 1;
    @Column(name = "triggered_at", nullable = false)
    private OffsetDateTime triggeredAt;
    @Column(name = "sent_at")
    private OffsetDateTime sentAt;
    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;
}
