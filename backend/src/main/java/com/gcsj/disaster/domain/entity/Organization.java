package com.gcsj.disaster.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "sys_organization")
public class Organization extends BaseEntity {
    @Column(nullable = false, length = 128)
    private String name;
    @Column(unique = true, nullable = false, length = 64)
    private String code;
    @Column(name = "parent_id")
    private Long parentId;
    @Column(nullable = false)
    private Integer sort = 0;
}
