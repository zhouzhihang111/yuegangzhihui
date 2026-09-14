package com.yuegang.zhihui.notification.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record AdminSendNotificationRequest(
        @NotBlank String userId,
        @NotBlank String templateCode,
        @NotNull @Size(max = 50) Map<String, String> variables) {}
