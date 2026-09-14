package com.yuegang.zhihui.auth.api.dto;

import java.time.OffsetDateTime;

public record CaptchaResponse(String challengeId, String mimeType, String imageBase64, OffsetDateTime expiresAt) { }
