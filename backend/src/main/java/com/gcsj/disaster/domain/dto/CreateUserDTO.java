package com.gcsj.disaster.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Data
public class CreateUserDTO {
    @NotBlank
    @Size(min = 3, max = 64)
    private String username;

    @NotBlank
    @Size(min = 6, max = 64)
    private String password;

    @Size(max = 64)
    private String realName;

    @Pattern(regexp = "^[0-9+\\-]{6,32}$", message = "手机号格式不正确")
    private String phone;

    @Email
    private String email;

    private Long orgId;

    private Set<Long> roleIds;
}
