package com.gcsj.disaster.config;

import com.gcsj.disaster.repository.UserRepository;
import com.gcsj.disaster.service.IUserService;
import com.gcsj.disaster.utils.JwtUtil;
import com.gcsj.disaster.utils.SecurityContextUtil.AuthUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JWT 鉴权过滤器
 * 解析 Authorization: Bearer xxx, 校验通过后写入 SecurityContext
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final JwtUtil.JwtProps jwtProps;
    private final IUserService userService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader(jwtProps.getHeader());
        if (header != null && header.startsWith(jwtProps.getPrefix())) {
            String token = header.substring(jwtProps.getPrefix().length());
            if (jwtUtil.isValid(token)) {
                Long userId = jwtUtil.extractUserId(token);
                String username = jwtUtil.extractUsername(token);
                Set<String> perms = userService.loadPermissionsByUserId(userId);
                AuthUser principal = new AuthUser(userId, username, perms);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal, null,
                        perms.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toSet()));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        chain.doFilter(req, resp);
    }
}
