package com.yuegang.zhihui.auth.application;
import com.yuegang.zhihui.common.security.InternalServiceSignature;
import java.time.*;import java.util.*;import org.springframework.web.client.RestClient;

final class PasswordResetNotificationClient {
    private static final String PATH="/internal/v1/notifications";
    private final RestClient client;private final InternalServiceSignature signatures;private final Clock clock;
    PasswordResetNotificationClient(String baseUrl,byte[] secret,Clock clock){client=RestClient.builder().baseUrl(baseUrl).build();this.clock=clock;signatures=new InternalServiceSignature(secret,clock,Duration.ofSeconds(30));}
    void deliver(long userId,String token,long expiresMinutes){Instant now=clock.instant();var metadata=new InternalServiceSignature.Metadata("ygh-auth-service","POST",PATH,now);client.post().uri(PATH).header("X-YGH-Service","ygh-auth-service").header("X-YGH-Service-Timestamp",Long.toString(now.toEpochMilli())).header("X-YGH-Service-Signature",signatures.sign(metadata)).body(Map.of("eventId","password-reset-"+UUID.randomUUID().toString().replace("-",""),"userId",Long.toString(userId),"templateCode","AUTH_PASSWORD_RESET","variables",Map.of("resetToken",token,"expiresMinutes",Long.toString(expiresMinutes)))).retrieve().toBodilessEntity();}
}
