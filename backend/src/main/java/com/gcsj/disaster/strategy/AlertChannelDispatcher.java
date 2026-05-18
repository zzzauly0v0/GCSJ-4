package com.gcsj.disaster.strategy;

import com.gcsj.disaster.domain.entity.Alert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通道分发器 - 按 channelKey 选择策略
 * Map<String, AlertChannelStrategy> 注入即自动汇聚所有 @Component 策略
 */
@Slf4j
@Component
public class AlertChannelDispatcher {

    private final Map<String, AlertChannelStrategy> strategies = new HashMap<>();

    public AlertChannelDispatcher(List<AlertChannelStrategy> all) {
        all.forEach(s -> strategies.put(s.channelKey(), s));
        log.info("alert channels registered: {}", strategies.keySet());
    }

    public boolean dispatch(Alert alert, List<String> channels) {
        if (channels == null || channels.isEmpty()) {
            channels = List.of("in_site");
        }
        boolean anyOk = false;
        for (String key : channels) {
            AlertChannelStrategy s = strategies.get(key);
            if (s == null) {
                log.warn("unknown channel: {}", key);
                continue;
            }
            anyOk |= s.dispatch(alert);
        }
        return anyOk;
    }
}
