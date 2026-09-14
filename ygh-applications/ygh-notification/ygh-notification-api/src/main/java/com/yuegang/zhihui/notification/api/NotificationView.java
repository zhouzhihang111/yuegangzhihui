package com.yuegang.zhihui.notification.api;import java.time.OffsetDateTime;public record NotificationView(String id,String title,String content,String status,boolean read,OffsetDateTime createdAt){}
