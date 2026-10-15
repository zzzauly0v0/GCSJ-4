package com.gcsj.disaster.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "sys_permission", schema = "sys")
public class Permission extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String name;

    @Column(unique = true, nullable = false, length = 128)
    private String code;

    @Column(nullable = false)
    private Short type = 1;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(length = 256)
    private String path;

    @Column(length = 64)
    private String icon;

    @Column(nullable = false)
    private Integer sort = 0;
}
