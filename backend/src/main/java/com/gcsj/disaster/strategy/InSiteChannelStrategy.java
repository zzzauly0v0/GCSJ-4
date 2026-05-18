package com.gcsj.disaster.strategy;

import com.gcsj.disaster.domain.converter.AlertConverter;
import com.gcsj.disaster.domain.entity.Alert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 站内信通道 - 走 WebSocket /topic/alerts 广播
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InSiteChannelStrategy implements AlertChannelStrategy {

    private final SimpMessagingTemplate messaging;
    private final AlertConverter alertConverter;

    @Override
    public String channelKey() {
        return "in_site";
    }

    @Override
    public boolean dispatch(Alert alert) {
        try {
            messaging.convertAndSend("/topic/alerts", alertConverter.toVO(alert));
            log.info("[InSite] alert {} pushed to /topic/alerts", alert.getCode());
            return true;
        } catch (Exception e) {
            log.error("[InSite] dispatch failed", e);
            return false;
        }
    }
}
