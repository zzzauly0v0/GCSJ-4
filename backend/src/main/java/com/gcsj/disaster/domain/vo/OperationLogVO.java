package com.gcsj.disaster.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class OperationLogVO {
    private Long id;
    private Long userId;
    private String username;
    private String module;
    private String action;
    private String method;
    private String uri;
    private String ip;
    private Integer resultCode;
    private Long costMs;
    private OffsetDateTime createdAt;
}
