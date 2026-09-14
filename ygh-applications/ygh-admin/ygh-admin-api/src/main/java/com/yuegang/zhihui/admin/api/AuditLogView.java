package com.yuegang.zhihui.admin.api;

import java.time.OffsetDateTime;

public record AuditLogView(OffsetDateTime timestamp, String userId, String module, String action,
                           String result, int status, String traceId, String message) {}
