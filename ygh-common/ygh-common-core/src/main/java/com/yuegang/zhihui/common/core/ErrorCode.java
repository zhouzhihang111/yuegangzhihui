package com.yuegang.zhihui.common.core;

/**
 * Stable cross-service error codes used by the external API envelope.
 */
public enum ErrorCode {
    SUCCESS("SUCCESS", "操作成功"),
    VALIDATION_ERROR("VALIDATION_ERROR", "参数校验失败"),
    UNAUTHENTICATED("UNAUTHENTICATED", "未登录或登录已失效"),
    PERMISSION_DENIED("PERMISSION_DENIED", "无权执行该操作"),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", "资源不存在"),
    BUSINESS_CONFLICT("BUSINESS_CONFLICT", "业务状态冲突"),
    RATE_LIMITED("RATE_LIMITED", "请求过于频繁"),
    DEPENDENCY_UNAVAILABLE("DEPENDENCY_UNAVAILABLE", "依赖服务暂不可用"),
    INTERNAL_ERROR("INTERNAL_ERROR", "系统内部错误");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
