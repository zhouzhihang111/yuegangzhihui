package com.yuegang.zhihui.ai.domain;

/** 外部模型提供方返回的安全、结构化错误，不包含凭据和原始请求。 */
public final class ModelProviderException extends RuntimeException {
    private final int httpStatus;
    private final String providerCode;
    private final String providerMessage;

    public ModelProviderException(int httpStatus, String providerCode, String providerMessage, Throwable cause) {
        super("model provider request failed", cause);
        this.httpStatus = httpStatus;
        this.providerCode = safe(providerCode, "UNKNOWN");
        this.providerMessage = safe(providerMessage, "未返回错误说明");
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String providerCode() {
        return providerCode;
    }

    public String providerMessage() {
        return providerMessage;
    }

    public String userMessage() {
        String normalized = providerCode.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("modelnotopen")) {
            return "豆包模型尚未开通，请先在火山方舟控制台开通当前对话模型，或在后台填写已开通的 Endpoint ID";
        }
        if (normalized.contains("modelnotfound") || normalized.contains("endpointnotfound")) {
            return "豆包未找到当前模型，请在后台填写此 API Key 已开通的模型 ID 或 Endpoint ID";
        }
        if (httpStatus == 401 || httpStatus == 403 || normalized.contains("auth")) {
            return "豆包 API Key 无效或没有当前模型的调用权限";
        }
        if (normalized.contains("balance") || normalized.contains("quota") || normalized.contains("overdue")) {
            return "豆包账户额度或余额不足，请在火山方舟控制台检查计费状态";
        }
        if (httpStatus == 429 || normalized.contains("ratelimit")) {
            return "豆包请求频率超限，请稍后再试";
        }
        return "豆包调用失败（HTTP " + httpStatus + "，错误码 " + providerCode + "）";
    }

    private static String safe(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String sanitized = value.replaceAll("[\\r\\n\\t]", " ")
                .replaceAll("(?i)bearer\\s+[a-z0-9._-]+", "Bearer [REDACTED]")
                .replaceAll("(?i)\\bsk-[a-z0-9_-]+", "[REDACTED]")
                .replaceAll("(?i)account\\s+\\d+", "account [REDACTED]")
                .replaceAll("(?i)request\\s*id\\s*:\\s*[a-z0-9_-]+", "Request id: [REDACTED]");
        return sanitized.length() > 300 ? sanitized.substring(0, 300) : sanitized;
    }
}
