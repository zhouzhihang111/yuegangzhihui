package com.yuegang.zhihui.notification.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record SaveNotificationTemplateRequest(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}") String code,
        @NotBlank @Size(max = 200) String titleTemplate,
        @NotBlank @Size(max = 10000) String contentTemplate,
        @NotBlank @Pattern(regexp = "IN_APP") String channel,
        boolean enabled,
        @PositiveOrZero long version) {}
