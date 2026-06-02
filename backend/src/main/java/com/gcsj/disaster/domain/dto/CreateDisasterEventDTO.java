package com.gcsj.disaster.domain.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class CreateDisasterEventDTO {
    @NotBlank
    private String title;
    @NotBlank
    private String type;
    @NotNull @Min(1) @Max(4)
    private Short level;
    @NotNull
    private Double longitude;
    @NotNull
    private Double latitude;
    /** 影响区域 GeoJSON Polygon, 可选 */
    private String affectedAreaGeoJson;
    @NotNull
    private OffsetDateTime occurredAt;
    private String description;

    /** 1 进行中 / 2 处置中 / 3 已结束, 不传按 1 处理 */
    @Min(1) @Max(3)
    private Short status;

    /** 仅 status=3 时有意义 */
    private OffsetDateTime endAt;
}
