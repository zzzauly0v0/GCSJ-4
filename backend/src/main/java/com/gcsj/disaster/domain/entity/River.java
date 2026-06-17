package com.gcsj.disaster.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.locationtech.jts.geom.LineString;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "gis_river", schema = "gis")
public class River {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column
    private Short grade;

    @Column(nullable = false, columnDefinition = "geometry(LineString, 4326)")
    private LineString geom;

    @Column(name = "length_km", precision = 10, scale = 2)
    private BigDecimal lengthKm;
}
