package com.gcsj.disaster.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.time.OffsetDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "biz_disaster_event", schema = "biz")
public class DisasterEvent extends BaseEntity {
    @Column(unique = true, nullable = false, length = 64)
    private String code;
    @Column(nullable = false, length = 256)
    private String title;
    @Column(nullable = false, length = 32)
    private String type;
    @Column(nullable = false)
    private Short level;
    @Column(nullable = false, columnDefinition = "geometry(Point, 4326)")
    private Point location;
    @Column(name = "affected_area", columnDefinition = "geometry(Polygon, 4326)")
    private Polygon affectedArea;
    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;
    @Column(name = "end_at")
    private OffsetDateTime endAt;
    @Column(nullable = false)
    private Short status = 1;
    @Column(columnDefinition = "text")
    private String description;
    @Column(name = "reporter_id")
    private Long reporterId;
}
