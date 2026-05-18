package com.gcsj.disaster.domain.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CreateSensorDTO {
    @NotBlank
    @Size(max = 64)
    private String code;
    @NotBlank
    @Size(max = 128)
    private String name;
    @NotBlank
    @Pattern(regexp = "rain_gauge|displacement|soil_moisture|weather_station")
    private String type;
    private Long orgId;
    @NotNull
    @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0")
    private Double longitude;
    @NotNull
    @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0")
    private Double latitude;
    private Double elevation;
    @Size(max = 16)
    private String unit;
    @Size(max = 512)
    private String description;
}
