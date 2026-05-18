package com.gcsj.disaster.config;

import com.gcsj.disaster.common.Result;
import com.gcsj.disaster.domain.entity.OperationLog;
import com.gcsj.disaster.repository.OperationLogRepository;
import com.gcsj.disaster.utils.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 操作日志异步写入器
 * 与 {@link OperationLogAspect} 分离, 让 @Async 在跨 Bean 调用时正确生效.
 * (类内自调用 + AOP 代理会绕过, 拆出独立 Bean 是最干净的写法.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationLogWriter {

    private final OperationLogRepository repository;

    @Async
    public void write(String method,
                      String uri,
                      String ip,
                      String module,
                      String action,
                      long costMs,
                      Object ret,
                      Throwable err) {
        try {
            // 跳过文档/健康检查
            if (uri == null || uri.startsWith("/swagger")
                    || uri.startsWith("/v3/api-docs")
                    || uri.startsWith("/actuator")) {
                return;
            }
            OperationLog entity = new OperationLog();
            SecurityContextUtil.AuthUser u = SecurityContextUtil.currentOrNull();
            if (u != null) {
                entity.setUserId(u.userId());
                entity.setUsername(u.username());
            }
            entity.setMethod(method);
            entity.setUri(uri);
            entity.setIp(ip);
            entity.setModule(module);
            entity.setAction(action);
            entity.setCostMs(costMs);
            if (ret instanceof Result<?> r) {
                entity.setResultCode(r.getCode());
            } else if (err != null) {
                entity.setResultCode(-1);
            }
            repository.save(entity);
        } catch (Exception e) {
            log.warn("write op log failed: {}", e.getMessage());
        }
    }
}
