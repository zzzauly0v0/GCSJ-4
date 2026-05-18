package com.gcsj.disaster.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.locationtech.jts.geom.Point;

import java.time.OffsetDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "biz_sensor")
public class Sensor extends BaseEntity {
    @Column(unique = true, nullable = false, length = 64)
    private String code;
    @Column(nullable = false, length = 128)
    private String name;
    @Column(nullable = false, length = 32)
    private String type;
    @Column(name = "org_id")
    private Long orgId;
    @Column(nullable = false, columnDefinition = "geometry(Point, 4326)")
    private Point location;
    private Double elevation;
    @Column(length = 16)
    private String unit;
    @Column(name = "install_at")
    private OffsetDateTime installAt;
    @Column(nullable = false)
    private Short status = 1;
    @Column(length = 512)
    private String description;
}
