package com.gcsj.disaster.domain.vo;

import lombok.Data;
import java.util.List;

@Data
public class PermissionVO {
    private Long id;
    private String name;
    private String code;
    private Short type;
    private Long parentId;
    private String path;
    private String icon;
    private Integer sort;
    private List<PermissionVO> children;
}
