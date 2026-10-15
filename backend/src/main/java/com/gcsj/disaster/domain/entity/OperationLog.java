package com.gcsj.disaster.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

@Data
@Entity
@Table(name = "sys_operation_log", schema = "sys")
@EntityListeners(AuditingEntityListener.class)
public class OperationLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id")
    private Long userId;
    @Column(length = 64)
    private String username;
    @Column(length = 64)
    private String module;
    @Column(length = 64)
    private String action;
    @Column(length = 16)
    private String method;
    @Column(length = 256)
    private String uri;
    @Column(length = 64)
    private String ip;
    @Column(name = "result_code")
    private Integer resultCode;
    @Column(name = "cost_ms")
    private Long costMs;
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
