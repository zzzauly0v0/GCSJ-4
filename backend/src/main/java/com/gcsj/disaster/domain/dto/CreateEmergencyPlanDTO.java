package com.gcsj.disaster.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateEmergencyPlanDTO {
    @NotBlank
    private String code;
    @NotBlank
    private String name;
    private String disasterType;
    @Min(1) @Max(4)
    private Short level;
    private String content;
    private String fileUrl;
    private Boolean enabled = true;
}
