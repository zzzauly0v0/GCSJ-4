package com.gcsj.disaster.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一错误码
 * 0           : 成功
 * 1xxxx       : 系统级
 * 2xxxx       : 用户/认证
 * 4xxxx       : 预警/事件
 * 5xxxx       : 空间/图层
 * 6xxxx       : 字典
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

    // 预警/事件
    ALERT_NOT_FOUND(40101, "预警事件不存在"),
    DISASTER_EVENT_NOT_FOUND(40201, "灾害事件不存在"),
    ALERT_DISPATCH_FAILED(40301, "预警发布失败"),

    // 空间/图层
    LAYER_NOT_FOUND(50001, "图层不存在"),
    GEOMETRY_INVALID(50002, "空间数据格式不合法"),

    // 字典
    DICTIONARY_NOT_FOUND(60001, "字典项不存在"),
    DICTIONARY_DUPLICATE(60002, "同类型下字典项编码已存在"),

    // 第三方
    GEOSERVER_ERROR(90002, "GeoServer 调用错误");

    private final Integer code;
    private final String message;
}
