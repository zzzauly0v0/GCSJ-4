package com.gcsj.disaster.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class CreateObservationDTO {
    @NotNull
    private Long sensorId;
    @NotBlank
    private String indicator;
    @NotNull
    private Double value;
    private String unit;
    private OffsetDateTime observedAt;
}
