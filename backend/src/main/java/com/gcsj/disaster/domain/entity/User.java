package com.gcsj.disaster.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "sys_user")
public class User extends BaseEntity {

    @Column(unique = true, nullable = false, length = 64)
    private String username;

    @Column(nullable = false, length = 128)
    private String password;

    @Column(name = "real_name", length = 64)
    private String realName;

    @Column(length = 32)
    private String phone;

    @Column(length = 128)
    private String email;

    @Column(length = 256)
    private String avatar;

    @Column(nullable = false)
    private Short status = 1;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;
}
