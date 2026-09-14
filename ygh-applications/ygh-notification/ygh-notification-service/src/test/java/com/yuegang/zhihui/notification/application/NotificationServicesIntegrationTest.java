package com.yuegang.zhihui.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.test.YghTestContainerFactory;
import com.yuegang.zhihui.notification.api.NotificationCommand;
import com.yuegang.zhihui.notification.api.SaveNotificationTemplateRequest;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class NotificationServicesIntegrationTest {
    @Test
    void deliversReadsRetriesDeadLettersAndReplaysIdempotently() {
        try (var mysql = YghTestContainerFactory.mysql().start()) {
            Flyway.configure().dataSource(mysql.jdbcUrl(), mysql.username(), mysql.credential())
                    .locations("classpath:db/migration").load().migrate();
            var dataSource = new DriverManagerDataSource(
                    mysql.jdbcUrl(), mysql.username(), mysql.credential());
            var service = new NotificationService(dataSource);
            var query = new NotificationQueryService(dataSource);
            var jdbc = new JdbcTemplate(dataSource);
            var templates = new NotificationTemplateService(dataSource);

            assertThat(templates.list()).extracting(x -> x.code()).contains("ORDER_PAID", "AUTH_PASSWORD_RESET");
            var templateRequest = new SaveNotificationTemplateRequest(
                    "CUSTOM_ALERT", "提醒 {{subject}}", "内容 {{content}}", "IN_APP", true, 0);
            var template = templates.save("CUSTOM_ALERT", templateRequest);
            assertThat(template.code()).isEqualTo("CUSTOM_ALERT");
            var updatedTemplate = templates.save("CUSTOM_ALERT", new SaveNotificationTemplateRequest(
                    "CUSTOM_ALERT", "新提醒 {{subject}}", "新内容 {{content}}", "IN_APP", false, template.version()));
            assertThat(updatedTemplate.enabled()).isFalse();
            assertThatThrownBy(() -> templates.save("OTHER", templateRequest)).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> templates.save("CUSTOM_ALERT", templateRequest)).isInstanceOf(BusinessException.class);

            var command = new NotificationCommand(
                    "event-order-1", "42", "ORDER_PAID", Map.of("referenceId", "O20260713001"));
            var created = service.create(command);
            assertThat(created.content()).contains("O20260713001");
            assertThat(service.create(command).id()).isEqualTo(created.id());
            assertThat(query.unread(42)).isEqualTo(1);
            assertThat(service.list(42)).singleElement().extracting(x -> x.status()).isEqualTo("PENDING");
            assertThat(service.dispatchPending()).isEqualTo(1);
            assertThat(service.dispatchPending()).isZero();
            service.read(42, created.id());
            service.read(42, created.id());
            assertThat(query.unread(42)).isZero();
            assertThat(query.readAll(42)).isZero();
            assertThatThrownBy(() -> service.read(7, created.id())).isInstanceOf(BusinessException.class);

            var failed = service.create(new NotificationCommand(
                    "event-training-1", "43", "TRAINING_ASSIGNED", Map.of("courseTitle", "跨境政策")));
            long failedId = Long.parseLong(failed.id());
            service.fail(999999, null);
            service.fail(failedId, "temporary");
            service.fail(failedId, "temporary");
            service.fail(failedId, "temporary");
            service.fail(failedId, "temporary");
            service.fail(failedId, "x".repeat(1200));
            var dead = service.deadLetters();
            assertThat(dead).singleElement().satisfies(x -> {
                assertThat(x.messageId()).isEqualTo(failed.id());
                assertThat(x.failureReason()).hasSize(1000);
            });
            service.replay(dead.getFirst().id(), 9);
            assertThat(service.deadLetters()).isEmpty();
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM notification_message WHERE id=?", String.class, failedId))
                    .isEqualTo("PENDING");
            assertThat(service.dispatchPending()).isEqualTo(1);
            assertThatThrownBy(() -> service.replay("999999", 9)).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> service.replay("bad", 9)).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> service.create(new NotificationCommand(
                    "event-invalid", "44", "ORDER_PAID", Map.of())))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> service.create(new NotificationCommand(
                    "event-missing", "44", "MISSING", Map.of())))
                    .isInstanceOf(BusinessException.class);
        }
    }
}
