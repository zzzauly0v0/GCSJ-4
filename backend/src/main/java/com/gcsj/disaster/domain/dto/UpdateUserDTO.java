package com.gcsj.disaster.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Data
public class UpdateUserDTO {
    @Size(max = 64)
    private String realName;
    @Pattern(regexp = "^[0-9+\\-]{6,32}$")
    private String phone;
    @Email
    private String email;
    private Short status;
    private Set<Long> roleIds;
}
