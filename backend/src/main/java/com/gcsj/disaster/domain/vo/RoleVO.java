package com.gcsj.disaster.domain.vo;

import lombok.Data;
import java.util.List;

@Data
public class RoleVO {
    private Long id;
    private String name;
    private String code;
    private String description;
    private List<Long> permissionIds;
}
