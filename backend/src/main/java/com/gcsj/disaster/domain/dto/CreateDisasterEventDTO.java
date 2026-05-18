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
}
