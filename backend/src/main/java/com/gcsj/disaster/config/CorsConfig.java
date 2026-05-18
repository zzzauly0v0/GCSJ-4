package com.gcsj.disaster.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 跨域配置
 * 提供 Spring Security 自动识别的 CorsConfigurationSource Bean,
 * 与 WebSecurityConfig 中 .cors(Customizer.withDefaults()) 配合.
 */
@Configuration
public class CorsConfig {

    @Configuration
    @ConfigurationProperties(prefix = "gcsj.cors")
    @Data
    public static class CorsProps {
        private List<String> allowedOrigins = List.of("*");
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProps props) {
        CorsConfiguration cfg = new CorsConfiguration();
        if (props.getAllowedOrigins().contains("*")) {
            cfg.addAllowedOriginPattern("*");
        } else {
            props.getAllowedOrigins().forEach(cfg::addAllowedOriginPattern);
        }
        cfg.addAllowedHeader("*");
        cfg.addAllowedMethod("*");
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}
