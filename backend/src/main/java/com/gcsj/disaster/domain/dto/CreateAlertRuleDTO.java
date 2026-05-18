package com.gcsj.disaster.domain.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CreateAlertRuleDTO {
    @NotBlank
    private String name;
    @NotBlank
    private String indicator;
    @NotBlank
    @Pattern(regexp = ">|>=|<|<=|==")
    private String operator;
    @NotNull
    private Double threshold;
    @NotNull
    @Min(1) @Max(4)
    private Short level;
    private String disasterType;
    private Boolean enabled = true;
    private String description;
}
