package com.gcsj.disaster.strategy;

import com.gcsj.disaster.domain.entity.Alert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 短信通道 - 占位实现 (扩展点: 接入阿里云 / 腾讯云短信)
 */
@Slf4j
@Component
public class SmsChannelStrategy implements AlertChannelStrategy {
    @Override
    public String channelKey() {
        return "sms";
    }

    @Override
    public boolean dispatch(Alert alert) {
        log.info("[SMS] (mock) send alert {} to subscribers", alert.getCode());
        // TODO 集成短信网关
        return true;
    }
}
