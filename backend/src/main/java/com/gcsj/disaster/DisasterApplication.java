package com.gcsj.disaster;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启动入口
 * GCSJ-4 气象地质灾害监测预警管理系统
 */
@EnableAsync
@EnableScheduling
@SpringBootApplication
public class DisasterApplication {

    public static void main(String[] args) {
        SpringApplication.run(DisasterApplication.class, args);
    }
}
