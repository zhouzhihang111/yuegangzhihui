package com.yuegang.zhihui.product.application;

import com.yuegang.zhihui.common.security.InternalServiceSignature;
import com.yuegang.zhihui.search.api.DeleteDocumentCommand;
import com.yuegang.zhihui.search.api.IndexChunkCommand;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestClient;

public final class ProductSearchDispatcher {
    private static final String INDEX = "/internal/v1/search/index";
    private static final String DELETE = "/internal/v1/search/delete-document";
    private final JdbcTemplate jdbc;
    private final RestClient search;
    private final InternalServiceSignature signatures;

    public ProductSearchDispatcher(JdbcTemplate jdbc, String baseUrl, byte[] secret) {
        this.jdbc = jdbc;
        search = RestClient.builder().baseUrl(baseUrl).build();
        signatures = new InternalServiceSignature(secret, Clock.systemUTC(), Duration.ofSeconds(30));
    }

    @Scheduled(fixedDelayString = "${ygh.product.search-dispatch-delay:3000}")
    public void dispatch() {
        List<String> jobs = jdbc.queryForList("SELECT id FROM product_search_job WHERE status IN ('PENDING','RETRY') AND (next_retry_at IS NULL OR next_retry_at<=NOW(6)) ORDER BY created_at LIMIT 20", String.class);
        jobs.forEach(this::process);
    }

    private void process(String job) {
        if (jdbc.update("UPDATE product_search_job SET status='PROCESSING' WHERE id=? AND status IN ('PENDING','RETRY')", job) != 1) return;
        try {
            ProductSource source = jdbc.query("SELECT s.id,s.sku_code,s.price,s.currency,s.traceability_code,s.status,s.version,s.updated_at,p.name,p.description,c.name,b.name FROM product_search_job j JOIN product_sku s ON s.id=j.sku_id JOIN product_spu p ON p.id=s.spu_id JOIN product_category c ON c.id=p.category_id LEFT JOIN product_brand b ON b.id=p.brand_id WHERE j.id=?", result -> {
                if (!result.next()) throw new IllegalStateException("product search source is missing");
                return new ProductSource(result.getLong(1), result.getString(2), result.getBigDecimal(3), result.getString(4), result.getString(5), result.getString(6), result.getLong(7), result.getTimestamp(8).toInstant().atOffset(ZoneOffset.UTC), result.getString(9), result.getString(10), result.getString(11), result.getString(12));
            }, job);
            String document = "product:" + source.skuId();
            if ("OFF_SHELF".equals(source.status())) {
                send(DELETE, new DeleteDocumentCommand(document, "product-active"));
            } else if ("PUBLISHED".equals(source.status())) {
                String specifications = String.join(" ", jdbc.queryForList("SELECT CONCAT(spec_key,':',spec_value) FROM product_specification WHERE sku_id=? ORDER BY sort_order",String.class,source.skuId()));
                String content = String.join(" ", source.name(), value(source.description()), source.skuCode(), value(source.brand()), source.category(), value(source.traceabilityCode()), specifications, source.price().toPlainString(), source.currency());
                send(INDEX, new IndexChunkCommand(document, Long.toString(source.skuId()), source.name(), content, "PRODUCT", "PUBLIC", "product-active", source.version(), source.updatedAt(), true));
            }
            jdbc.update("UPDATE product_search_job SET status='SUCCEEDED',last_error=NULL WHERE id=?", job);
        } catch (RuntimeException failure) {
            String error = failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage());
            jdbc.update("UPDATE product_search_job SET status=CASE WHEN retry_count>=9 THEN 'FAILED' ELSE 'RETRY' END,retry_count=retry_count+1,next_retry_at=DATE_ADD(NOW(6),INTERVAL LEAST(300,POW(2,retry_count)) SECOND),last_error=? WHERE id=?", error.substring(0, Math.min(1000, error.length())), job);
        }
    }

    private void send(String path, Object body) {
        Instant now = Instant.now();
        var metadata = new InternalServiceSignature.Metadata("ygh-product-service", "POST", path, now);
        search.post().uri(path).header("X-YGH-Service", "ygh-product-service")
                .header("X-YGH-Service-Timestamp", Long.toString(now.toEpochMilli()))
                .header("X-YGH-Service-Signature", signatures.sign(metadata)).body(body).retrieve().toBodilessEntity();
    }

    private static String value(String value) { return value == null ? "" : value; }
    private record ProductSource(long skuId, String skuCode, BigDecimal price, String currency, String traceabilityCode,
                                 String status, long version, OffsetDateTime updatedAt, String name, String description,
                                 String category, String brand) {}
}
