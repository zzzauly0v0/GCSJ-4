package com.gcsj.disaster.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.locationtech.jts.geom.Polygon;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "gis_layer", schema = "gis")
public class Layer extends BaseEntity {
    @Column(nullable = false, length = 128)
    private String name;
    @Column(unique = true, nullable = false, length = 64)
    private String code;
    @Column(nullable = false, length = 32)
    private String type;       // vector / raster / wms / wmts / xyz
    @Column(name = "source_url", length = 512)
    private String sourceUrl;
    @Column(length = 64)
    private String workspace;
    @Column(name = "layer_name", length = 128)
    private String layerName;
    @Column(length = 128)
    private String style;
    @Column(nullable = false)
    private Boolean visible = Boolean.TRUE;
    @Column(name = "z_index", nullable = false)
    private Integer zIndex = 0;
    @Column(columnDefinition = "geometry(Polygon, 4326)")
    private Polygon extent;
    @Column(length = 512)
    private String description;
}
