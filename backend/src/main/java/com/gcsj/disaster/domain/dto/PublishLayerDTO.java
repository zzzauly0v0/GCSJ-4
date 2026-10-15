package com.gcsj.disaster.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 触发图层发布到 GeoServer 的请求体
 * 当前仅占位：实际发布逻辑在 GeoServerClient 落地后接入。
 */
@Data
public class PublishLayerDTO {
    /** PostGIS 表名（schema=public） */
    @NotBlank
    private String pgTable;
    /** 可选：覆盖默认数据存储 */
    private String datastore;
    /** 可选：覆盖图层默认样式 */
    private String style;
}
