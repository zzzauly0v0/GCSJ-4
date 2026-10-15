package com.gcsj.disaster.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateLayerDTO {
    @NotBlank
    private String name;
    @NotBlank
    private String code;
    @NotBlank
    @Pattern(regexp = "vector|raster|wms|wmts|xyz")
    private String type;
    private String sourceUrl;
    private String workspace;
    private String layerName;
    private String style;
    /** PostGIS 表名（仅用于 GeoServer 自动发布时传入，不入库） */
    private String pgTable;
    private Boolean visible = true;
    private Integer zIndex = 0;
    private String description;
}
