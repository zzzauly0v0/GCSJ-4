package com.gcsj.disaster.domain.event;

import com.gcsj.disaster.domain.entity.Alert;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 预警触发事件 - 观察者模式 (Spring Event)
 * 业务主流程发事件, 副作用监听处理: 推送 / 写日志
 */
@Getter
public class AlertTriggeredEvent extends ApplicationEvent {

    private final Alert alert;
    /** 推送通道, 如 in_site,sms,email */
    private final java.util.List<String> channels;

    public AlertTriggeredEvent(Object source, Alert alert, java.util.List<String> channels) {
        super(source);
        this.alert = alert;
        this.channels = channels;
    }
}
