package com.yuegang.zhihui.order.infrastructure;

import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.security.InternalServiceSignature;
import com.yuegang.zhihui.inventory.api.InventoryCommand;
import com.yuegang.zhihui.inventory.api.InventoryView;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

public final class InventoryClient {
    private final RestClient client;
    private final InternalServiceSignature signatures;

    public InventoryClient(String baseUrl, byte[] secret) {
        client = RestClient.builder().baseUrl(baseUrl).build();
        signatures = new InternalServiceSignature(secret, Clock.systemUTC(), Duration.ofSeconds(30));
    }

    public InventoryView get(String sku) {
        String path = "/internal/v1/inventory/" + sku;
        Instant now = Instant.now();
        var metadata = new InternalServiceSignature.Metadata("ygh-order-service", "GET", path, now);
        ApiResponse<InventoryView> response = client.get().uri(path)
                .header("X-YGH-Service", "ygh-order-service")
                .header("X-YGH-Service-Timestamp", Long.toString(now.toEpochMilli()))
                .header("X-YGH-Service-Signature", signatures.sign(metadata))
                .retrieve().body(new ParameterizedTypeReference<>() { });
        if (response == null || response.data() == null) throw new IllegalStateException("inventory response missing");
        return response.data();
    }

    public void reserve(InventoryCommand command) { post("/internal/v1/inventory/reserve", command); }
    public void confirm(InventoryCommand command) { post("/internal/v1/inventory/confirm", command); }
    public void release(InventoryCommand command) { post("/internal/v1/inventory/release", command); }
    public void returnSold(InventoryCommand command) { post("/internal/v1/inventory/return-sold", command); }

    private void post(String path, InventoryCommand command) {
        Instant now = Instant.now();
        var metadata = new InternalServiceSignature.Metadata("ygh-order-service", "POST", path, now);
        client.post().uri(path).header("X-YGH-Service", "ygh-order-service")
                .header("X-YGH-Service-Timestamp", Long.toString(now.toEpochMilli()))
                .header("X-YGH-Service-Signature", signatures.sign(metadata)).body(command)
                .retrieve().toBodilessEntity();
    }
}
