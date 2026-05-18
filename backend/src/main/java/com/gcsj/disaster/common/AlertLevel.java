package com.gcsj.disaster.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 预警等级 (蓝/黄/橙/红)
 * 颜色取自 CLAUDE.md
 */
@Getter
@AllArgsConstructor
public enum AlertLevel {
    BLUE(1, "蓝色", "#3B82F6"),
    YELLOW(2, "黄色", "#FBBF24"),
    ORANGE(3, "橙色", "#F97316"),
    RED(4, "红色", "#EF4444");

    private final int code;
    private final String label;
    private final String color;

    public static AlertLevel of(int code) {
        return Arrays.stream(values()).filter(v -> v.code == code).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown alert level: " + code));
    }
}
