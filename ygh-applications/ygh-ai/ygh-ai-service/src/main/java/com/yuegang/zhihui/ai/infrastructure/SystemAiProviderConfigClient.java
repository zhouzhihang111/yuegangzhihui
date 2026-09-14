package com.yuegang.zhihui.ai.infrastructure;

import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.security.InternalServiceSignature;
import com.yuegang.zhihui.system.api.InternalAiProviderConfig;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

public final class SystemAiProviderConfigClient {
    private static final String PATH = "/internal/v1/system/ai-provider-config";
    private static final String SERVICE = "ygh-ai-service";
    private final RestClient system;
    private final InternalServiceSignature signatures;
    private final Clock clock;
    private volatile InternalAiProviderConfig lastKnown;

    public SystemAiProviderConfigClient(String baseUrl, byte[] hmacSecret) {
        system = RestClient.builder().baseUrl(baseUrl).build();
        signatures = new InternalServiceSignature(hmacSecret, Clock.systemUTC(), Duration.ofSeconds(30));
        clock = Clock.systemUTC();
    }

    public InternalAiProviderConfig current() {
        Instant now = clock.instant();
        var metadata = new InternalServiceSignature.Metadata(SERVICE, "GET", PATH, now);
        try {
            ApiResponse<InternalAiProviderConfig> response = system.get().uri(PATH)
                    .header("X-YGH-Service", SERVICE)
                    .header("X-YGH-Service-Timestamp", Long.toString(now.toEpochMilli()))
                    .header("X-YGH-Service-Signature", signatures.sign(metadata))
                    .retrieve().body(new ParameterizedTypeReference<>() {});
            if (response == null || response.data() == null) throw new IllegalStateException("empty provider config");
            lastKnown = response.data();
            return response.data();
        } catch (RuntimeException unavailable) {
            if (lastKnown != null) return lastKnown;
            throw unavailable;
        }
    }
}
