package com.yuegang.zhihui.notification.api;

public record NotificationTemplateView(
        String code, String titleTemplate, String contentTemplate, String channel, boolean enabled, long version) {}
