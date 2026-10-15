package com.gcsj.disaster.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "gis_admin_region", schema = "gis")
public class AdminRegion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 12)
    private String adcode;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(nullable = false)
    private Short level;

    @Column(name = "parent_code", length = 12)
    private String parentCode;

    @Column(columnDefinition = "geometry(Point, 4326)")
    private Point center;

    @Column(columnDefinition = "geometry(MultiPolygon, 4326)")
    private MultiPolygon boundary;

    @Column(name = "area_km2", precision = 12, scale = 2)
    private BigDecimal areaKm2;
}
