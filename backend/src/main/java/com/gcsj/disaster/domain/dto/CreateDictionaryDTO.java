package com.gcsj.disaster.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateDictionaryDTO {
    @NotBlank
    @Size(max = 64)
    private String typeCode;

    @NotBlank
    @Size(max = 64)
    private String itemCode;

    @NotBlank
    @Size(max = 128)
    private String itemValue;

    private Integer sort = 0;

    @Size(max = 256)
    private String description;
}
