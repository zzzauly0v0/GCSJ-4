package com.gcsj.disaster.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final JwtProps props;
    private SecretKey key;

    @PostConstruct
    public void init() {
        byte[] keyBytes = props.getSecret().getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String issue(Long userId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", userId);
        claims.put("usr", username);
        long now = System.currentTimeMillis();
        long exp = now + props.getExpireMinutes() * 60_000L;
        return Jwts.builder()
                .claims(claims)
                .subject(String.valueOf(userId))
                .issuedAt(new Date(now))
                .expiration(new Date(exp))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }

    public Long extractUserId(String token) {
        Claims c = parse(token);
        Object uid = c.get("uid");
        if (uid instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(c.getSubject());
    }

    public String extractUsername(String token) {
        return (String) parse(token).get("usr");
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            log.debug("token invalid: {}", e.getMessage());
            return false;
        }
    }

    @lombok.Data
    @ConfigurationProperties(prefix = "gcsj.jwt")
    @Component
    public static class JwtProps {
        private String secret;
        private long expireMinutes = 720;
        private String header = "Authorization";
        private String prefix = "Bearer ";
    }
}
