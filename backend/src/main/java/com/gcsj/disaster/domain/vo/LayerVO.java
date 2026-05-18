package com.gcsj.disaster.domain.vo;

import lombok.Data;

@Data
public class LayerVO {
    private Long id;
    private String name;
    private String code;
    private String type;
    private String sourceUrl;
    private String workspace;
    private String layerName;
    private String style;
    private Boolean visible;
    private Integer zIndex;
    private String description;
}
