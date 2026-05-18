package com.gcsj.disaster.domain.vo;

import lombok.Data;

import java.util.List;

@Data
public class OrganizationVO {
    private Long id;
    private String name;
    private String code;
    private Long parentId;
    private Integer sort;
    private List<OrganizationVO> children;
}
