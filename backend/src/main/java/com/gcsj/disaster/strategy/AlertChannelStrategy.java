package com.gcsj.disaster.strategy;

import com.gcsj.disaster.domain.entity.Alert;

/**
 * 预警推送通道策略接口 (策略模式)
 * 实现类通过 channelKey() 自动注册到 Map<String, AlertChannelStrategy>, 调用方按 key 选择
 */
public interface AlertChannelStrategy {

    /** 通道标识: in_site / sms / email / wechat ... */
    String channelKey();

    /** 发送; 返回 true 表示已成功投递到通道 */
    boolean dispatch(Alert alert);
}
