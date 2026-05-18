package com.gcsj.disaster.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 操作日志 AOP - 横切关注点, 不污染业务代码
 * 拦截 controller 包下所有 public 方法, 调用 {@link OperationLogWriter} 异步落库.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogWriter writer;

    @Around("execution(public * com.gcsj.disaster.controller..*.*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        Object ret = null;
        Throwable err = null;
        try {
            ret = pjp.proceed();
            return ret;
        } catch (Throwable t) {
            err = t;
            throw t;
        } finally {
            try {
                ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attrs != null) {
                    HttpServletRequest req = attrs.getRequest();
                    writer.write(
                            req.getMethod(),
                            req.getRequestURI(),
                            req.getRemoteAddr(),
                            pjp.getSignature().getDeclaringType().getSimpleName(),
                            pjp.getSignature().getName(),
                            System.currentTimeMillis() - start,
                            ret,
                            err
                    );
                }
            } catch (Exception e) {
                log.warn("submit op log failed: {}", e.getMessage());
            }
        }
    }
}
