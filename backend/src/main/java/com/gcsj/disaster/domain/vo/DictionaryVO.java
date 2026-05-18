package com.gcsj.disaster.domain.vo;

import lombok.Data;

@Data
public class DictionaryVO {
    private Long id;
    private String typeCode;
    private String itemCode;
    private String itemValue;
    private Integer sort;
    private String description;
}
