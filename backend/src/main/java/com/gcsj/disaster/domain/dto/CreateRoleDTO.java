package com.gcsj.disaster.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Set;

@Data
public class CreateRoleDTO {
    @NotBlank
    private String name;
    @NotBlank
    private String code;
    private String description;
    private Set<Long> permissionIds;
}
