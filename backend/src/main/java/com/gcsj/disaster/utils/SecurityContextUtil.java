package com.gcsj.disaster.utils;

import com.gcsj.disaster.common.BusinessException;
import com.gcsj.disaster.common.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录上下文工具
 */
public final class SecurityContextUtil {

    private SecurityContextUtil() {}

    public static AuthUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof AuthUser u)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return u;
    }

    public static AuthUser currentOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser u)) return null;
        return u;
    }

    public record AuthUser(Long userId, String username, java.util.Set<String> permissions) {}
}
