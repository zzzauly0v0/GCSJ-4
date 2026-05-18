package com.gcsj.disaster.domain.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "sys_dictionary")
public class Dictionary {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "type_code", nullable = false, length = 64)
    private String typeCode;
    @Column(name = "item_code", nullable = false, length = 64)
    private String itemCode;
    @Column(name = "item_value", nullable = false, length = 128)
    private String itemValue;
    @Column(nullable = false)
    private Integer sort = 0;
    @Column(length = 256)
    private String description;
}
