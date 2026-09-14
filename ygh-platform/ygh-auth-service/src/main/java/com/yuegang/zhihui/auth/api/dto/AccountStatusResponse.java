package com.yuegang.zhihui.auth.api.dto;

import java.time.OffsetDateTime;

public record AccountStatusResponse(String userId, String status, long version, OffsetDateTime updatedAt) { }
