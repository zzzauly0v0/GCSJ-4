package com.gcsj.disaster.service;

/**
 * 气象数据采集服务
 * 模块 1: 数据采集与接入
 */
public interface IWeatherIngestService {
    /** 拉取一次气象数据并写入观测表 (返回写入条数) */
    int pullOnce();
}
