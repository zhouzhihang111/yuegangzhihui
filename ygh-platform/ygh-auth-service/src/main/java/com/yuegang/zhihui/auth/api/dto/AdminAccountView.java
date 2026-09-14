package com.yuegang.zhihui.auth.api.dto;
import java.time.OffsetDateTime;
public record AdminAccountView(String accountId,String userId,String principal,String accountType,String status,int failedLoginCount,OffsetDateTime lockedUntil,OffsetDateTime lastLoginAt,long version,OffsetDateTime createdAt){}
