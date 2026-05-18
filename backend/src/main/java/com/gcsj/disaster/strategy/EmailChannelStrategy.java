package com.gcsj.disaster.strategy;

import com.gcsj.disaster.domain.entity.Alert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 邮件通道 - 占位实现 (扩展点: 接入 spring-boot-starter-mail)
 */
@Slf4j
@Component
public class EmailChannelStrategy implements AlertChannelStrategy {
    @Override
    public String channelKey() {
        return "email";
    }

    @Override
    public boolean dispatch(Alert alert) {
        log.info("[Email] (mock) send alert {} to ops mail group", alert.getCode());
        return true;
    }
}
