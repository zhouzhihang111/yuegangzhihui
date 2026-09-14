package com.yuegang.zhihui.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.common.security.InternalServiceSignature;
import com.yuegang.zhihui.common.test.YghTestContainerFactory;
import com.yuegang.zhihui.knowledge.api.KnowledgeStatus;
import com.yuegang.zhihui.knowledge.api.ReviewKnowledgeRequest;
import com.yuegang.zhihui.knowledge.api.UpdateKnowledgeMetadataRequest;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;

class KnowledgeServicesIntegrationTest {
    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    @TempDir Path storage;

    @Test
    void supportsSecureUploadMetadataReviewPublicationIndexOfflineRejectAndExpiry() throws Exception {
        try (var mysql = YghTestContainerFactory.mysql().start()) {
            Flyway.configure().dataSource(mysql.jdbcUrl(), mysql.username(), mysql.credential())
                    .locations("classpath:db/migration").load().migrate();
            var dataSource = new DriverManagerDataSource(mysql.jdbcUrl(), mysql.username(), mysql.credential());
            var documents = new KnowledgeDocumentService(dataSource, storage.toString());
            var lifecycle = new KnowledgeLifecycleService(dataSource);
            var metadata = new KnowledgeMetadataService(dataSource, new ObjectMapper());
            var access = new KnowledgeAccessGuard(dataSource);
            var jdbc = new JdbcTemplate(dataSource);

            var uploaded = documents.upload(42, "跨境通关政策", "政策法规", text("policy.md", "跨境商品通关需要完成申报、查验与放行。"));
            assertThat(uploaded.status()).isEqualTo(KnowledgeStatus.PENDING_REVIEW);
            assertThat(storage.resolve(uploaded.id() + ".bin")).exists();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_chunk WHERE document_id=?", Integer.class, Long.parseLong(uploaded.id()))).isPositive();
            assertBusinessError(() -> documents.upload(42, "非法", "POLICY", text("../escape.txt", "content")), ErrorCode.VALIDATION_ERROR);
            assertBusinessError(() -> documents.upload(42, "空文档", "POLICY", text("empty.txt", "")), ErrorCode.VALIDATION_ERROR);

            var updatedMetadata = metadata.update(42, uploaded.id(), new UpdateKnowledgeMetadataRequest("海关总署",
                    LocalDate.of(2026, 1, 1), OffsetDateTime.of(2027, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC), "CN",
                    "PUBLIC", "政策库", Set.of("通关", "政策"), 0));
            assertThat(updatedMetadata.tags()).containsExactly("政策", "通关");
            assertBusinessError(() -> metadata.update(42, uploaded.id(), new UpdateKnowledgeMetadataRequest(null,
                    LocalDate.of(2027, 1, 1), OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC), null,
                    "PUBLIC", null, Set.of(), updatedMetadata.metadataVersion())), ErrorCode.VALIDATION_ERROR);

            var published = documents.review(7, uploaded.id(), new ReviewKnowledgeRequest(ReviewKnowledgeRequest.Decision.APPROVE, "审核通过", uploaded.version()));
            assertThat(published.status()).isEqualTo(KnowledgeStatus.PUBLISHED);
            access.requirePublished(Long.parseLong(published.id()), Set.of("PUBLIC"));
            assertBusinessError(() -> access.requirePublished(Long.parseLong(published.id()), Set.of("INTERNAL")),
                    ErrorCode.RESOURCE_NOT_FOUND);
            assertThat(lifecycle.list(null, "政策法规", true, 0)).containsExactly(published);
            assertBusinessError(() -> documents.review(7, uploaded.id(), new ReviewKnowledgeRequest(ReviewKnowledgeRequest.Decision.APPROVE, null, uploaded.version())), ErrorCode.BUSINESS_CONFLICT);

            var receivedPaths = new ArrayList<String>();
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/internal/v1/search", exchange -> {
                String service = exchange.getRequestHeaders().getFirst("X-YGH-Service");
                Instant at = Instant.ofEpochMilli(Long.parseLong(exchange.getRequestHeaders().getFirst("X-YGH-Service-Timestamp")));
                String signature = exchange.getRequestHeaders().getFirst("X-YGH-Service-Signature");
                var signed = new InternalServiceSignature.Metadata(service, "POST", exchange.getRequestURI().getPath(), at);
                assertThat(new InternalServiceSignature(SECRET, Clock.systemUTC(), Duration.ofSeconds(30)).verify(signed, signature)).isTrue();
                receivedPaths.add(exchange.getRequestURI().getPath());
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
            });
            server.start();
            try {
                var dispatcher = new KnowledgeIndexDispatcher(jdbc, "http://127.0.0.1:" + server.getAddress().getPort(), SECRET);
                dispatcher.dispatch();
                assertThat(receivedPaths).contains("/internal/v1/search/index");
                assertThat(jdbc.queryForObject("SELECT status FROM knowledge_index_job ORDER BY created_at LIMIT 1", String.class)).isEqualTo("SUCCEEDED");
                var offline = lifecycle.offline(7, uploaded.id(), published.version(), "政策已替换");
                assertThat(offline.status()).isEqualTo(KnowledgeStatus.OFFLINE);
                dispatcher.dispatch();
                assertThat(receivedPaths).contains("/internal/v1/search/delete-document");
            } finally {
                server.stop(0);
            }

            var rejectedUpload = documents.upload(42, "驳回文档", "PRODUCT", text("reject.txt", "商品资料待修订"));
            assertThat(documents.review(7, rejectedUpload.id(), new ReviewKnowledgeRequest(ReviewKnowledgeRequest.Decision.REJECT, "来源不完整", 0)).status())
                    .isEqualTo(KnowledgeStatus.REJECTED);

            var internalUpload = documents.upload(42, "内部通关指引", "CUSTOMS", text("internal.txt", "内部流程"));
            metadata.update(42, internalUpload.id(), new UpdateKnowledgeMetadataRequest(null, null, null, "CN",
                    "INTERNAL", "内部知识库", Set.of("内部"), 0));
            var internalPublished = documents.review(7, internalUpload.id(),
                    new ReviewKnowledgeRequest(ReviewKnowledgeRequest.Decision.APPROVE, "内部发布", 0));
            access.requirePublished(Long.parseLong(internalPublished.id()), Set.of("PUBLIC", "INTERNAL"));
            assertBusinessError(() -> access.requirePublished(Long.parseLong(internalPublished.id()), Set.of("PUBLIC")),
                    ErrorCode.RESOURCE_NOT_FOUND);

            var expiringUpload = documents.upload(42, "临时政策", "POLICY", text("expire.txt", "临时监管政策"));
            var expiring = documents.review(7, expiringUpload.id(), new ReviewKnowledgeRequest(ReviewKnowledgeRequest.Decision.APPROVE, null, 0));
            try (var connection = DriverManager.getConnection(mysql.jdbcUrl(), mysql.username(), mysql.credential())) {
                connection.createStatement().executeUpdate("UPDATE knowledge_document SET expires_at=DATE_SUB(NOW(),INTERVAL 1 MINUTE) WHERE id=" + expiring.id());
            }
            new KnowledgeExpiryJob(jdbc, new DataSourceTransactionManager(dataSource)).expire();
            assertThat(documents.get(Long.parseLong(expiring.id())).status()).isEqualTo(KnowledgeStatus.EXPIRED);
            assertThat(lifecycle.list("REJECTED", null, false, 1000)).hasSize(1);
            assertBusinessError(() -> lifecycle.offline(7, expiring.id(), expiring.version(), "invalid"), ErrorCode.BUSINESS_CONFLICT);
            assertBusinessError(() -> metadata.view(Long.MAX_VALUE), ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private static MockMultipartFile text(String name, String content) {
        return new MockMultipartFile("file", name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertBusinessError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.errorCode()).isEqualTo(expected));
    }
}
