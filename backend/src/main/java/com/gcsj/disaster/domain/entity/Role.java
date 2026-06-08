package com.gcsj.disaster.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "sys_role", schema = "sys")
public class Role extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String name;

    @Column(unique = true, nullable = false, length = 64)
    private String code;

    @Column(length = 256)
    private String description;
}
