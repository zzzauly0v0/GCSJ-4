package com.gcsj.disaster.listener;

import com.gcsj.disaster.domain.event.AlertTriggeredEvent;
import com.gcsj.disaster.strategy.AlertChannelDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.OffsetDateTime;

/**
 * 预警触发监听器 - 观察者模式
 * 在事务提交后异步通过策略分发器推送
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertEventListener {

    private final AlertChannelDispatcher dispatcher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAlert(AlertTriggeredEvent event) {
        boolean ok = dispatcher.dispatch(event.getAlert(), event.getChannels());
        if (ok) {
            event.getAlert().setSentAt(OffsetDateTime.now());
            event.getAlert().setStatus((short) 2);
            // 注: 真实场景需要重新保存; 此处简化, 状态由 confirm/close 推进
        }
        log.info("alert dispatched: code={} ok={}", event.getAlert().getCode(), ok);
    }
}
