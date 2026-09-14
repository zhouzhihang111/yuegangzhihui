package com.yuegang.zhihui.notification.api;
import java.time.OffsetDateTime;
public record NotificationDeadLetterView(String id,String messageId,String eventId,String failureReason,OffsetDateTime failedAt){}
