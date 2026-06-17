package com.gcsj.disaster.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.locationtech.jts.geom.Point;

@Data
@Entity
@Table(name = "gis_settlement", schema = "gis")
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 32)
    private String type;

    @Column
    private Integer population;

    @Column(nullable = false, columnDefinition = "geometry(Point, 4326)")
    private Point geom;
}
