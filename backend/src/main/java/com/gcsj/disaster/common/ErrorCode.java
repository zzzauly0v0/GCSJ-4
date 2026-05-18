package com.gcsj.disaster.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一错误码
 * 0           : 成功
 * 1xxxx       : 系统级
 * 2xxxx       : 用户/认证
 * 3xxxx       : 传感/观测
 * 4xxxx       : 预警/事件
 * 5xxxx       : 空间/图层
 * 9xxxx       : 第三方
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    SUCCESS(0, "success"),

    // 系统
    UNKNOWN_ERROR(10000, "服务器内部错误"),
    PARAM_INVALID(10001, "参数校验失败"),
    UNAUTHORIZED(10002, "未授权或登录已过期"),
    FORBIDDEN(10003, "无权限访问"),
    NOT_FOUND(10004, "资源不存在"),
    METHOD_NOT_ALLOWED(10005, "请求方法不支持"),

    // 用户/认证
    USER_NOT_FOUND(20001, "用户不存在"),
    USER_PASSWORD_ERROR(20002, "用户名或密码错误"),
    TOKEN_INVALID(20003, "token 无效"),
    TOKEN_EXPIRED(20004, "token 已过期"),
    USER_DISABLED(20005, "账号已停用"),
    USER_ALREADY_EXISTS(20006, "用户名已存在"),
    ROLE_NOT_FOUND(20101, "角色不存在"),
    PERMISSION_DENIED(20102, "权限不足"),

    // 传感/观测
    SENSOR_NOT_FOUND(30001, "传感器不存在"),
    SENSOR_CODE_DUPLICATE(30002, "传感器编码已存在"),
    OBSERVATION_INVALID(30101, "观测数据非法"),

    // 预警/事件
    ALERT_RULE_NOT_FOUND(40001, "预警规则不存在"),
    ALERT_NOT_FOUND(40101, "预警事件不存在"),
    DISASTER_EVENT_NOT_FOUND(40201, "灾害事件不存在"),
    ALERT_DISPATCH_FAILED(40301, "预警发布失败"),

    // 空间/图层
    LAYER_NOT_FOUND(50001, "图层不存在"),
    GEOMETRY_INVALID(50002, "空间数据格式不合法"),

    // 第三方
    WEATHER_API_ERROR(90001, "气象数据接口错误"),
    GEOSERVER_ERROR(90002, "GeoServer 调用错误");

    private final Integer code;
    private final String message;
}
