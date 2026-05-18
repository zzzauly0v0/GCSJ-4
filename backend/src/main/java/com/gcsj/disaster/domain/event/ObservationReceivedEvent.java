package com.gcsj.disaster.domain.event;

import com.gcsj.disaster.domain.entity.Observation;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 监测数据入库后事件 - 由规则引擎监听并评估是否触发预警
 */
@Getter
public class ObservationReceivedEvent extends ApplicationEvent {
    private final Observation observation;
    public ObservationReceivedEvent(Object source, Observation observation) {
        super(source);
        this.observation = observation;
    }
}
