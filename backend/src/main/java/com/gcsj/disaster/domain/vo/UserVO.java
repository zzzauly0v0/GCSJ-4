package com.gcsj.disaster.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
public class UserVO {
    private Long id;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private String avatar;
    private Short status;
    private OffsetDateTime lastLoginAt;
    private OffsetDateTime createdAt;
    private List<String> roleCodes;
    private List<String> permissions;
}
