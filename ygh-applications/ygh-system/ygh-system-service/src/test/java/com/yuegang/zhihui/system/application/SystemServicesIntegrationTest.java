package com.yuegang.zhihui.system.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.common.test.YghTestContainerFactory;
import com.yuegang.zhihui.system.api.AssignRolesRequest;
import com.yuegang.zhihui.system.api.SaveDictionaryItemRequest;
import com.yuegang.zhihui.system.api.SaveDictionaryTypeRequest;
import com.yuegang.zhihui.system.api.UpdateFeatureFlagRequest;
import com.yuegang.zhihui.system.api.UpdateSystemSettingRequest;
import com.yuegang.zhihui.system.api.UpdateAiProviderConfigRequest;
import com.yuegang.zhihui.system.infrastructure.JdbcAuthorizationRepository;
import com.yuegang.zhihui.system.security.SystemSecretCipher;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class SystemServicesIntegrationTest {
    @Test
    void managesRbacDictionariesFlagsAndSettingsWithOptimisticConcurrency() throws Exception {
        try (var mysql = YghTestContainerFactory.mysql().start()) {
            Flyway.configure().dataSource(mysql.jdbcUrl(), mysql.username(), mysql.credential())
                    .locations("classpath:db/migration").load().migrate();
            var dataSource = new DriverManagerDataSource(mysql.jdbcUrl(), mysql.username(), mysql.credential());
            var authorization = new AuthorizationService(new JdbcAuthorizationRepository(dataSource));
            assertThat(authorization.snapshot("42").roles()).containsExactly("USER");
            var assigned = authorization.assign("42", new AssignRolesRequest(Set.of("ADMIN"), 0, "initial"), 7);
            assertThat(assigned.roles()).containsExactly("ADMIN");
            assertThat(assigned.permissions()).contains("system:rbac:write", "system:user:status");
            assertBusinessError(() -> authorization.assign("42", new AssignRolesRequest(Set.of("MISSING"), 1, null), 7), ErrorCode.BUSINESS_CONFLICT);
            assertBusinessError(() -> authorization.assign("42", new AssignRolesRequest(Set.of(), 0, null), 7), ErrorCode.BUSINESS_CONFLICT);
            assertBusinessError(() -> authorization.snapshot("0"), ErrorCode.VALIDATION_ERROR);
            assertBusinessError(() -> authorization.snapshot("x"), ErrorCode.VALIDATION_ERROR);
            assertBusinessError(() -> authorization.assign("42", new AssignRolesRequest(Set.of(), 1, "x".repeat(501)), 7), ErrorCode.VALIDATION_ERROR);

            var dictionaries = new SystemDictionaryAdministrationService(dataSource);
            var type = dictionaries.saveType(new SaveDictionaryTypeRequest("ORDER_STATUS", "订单状态", true, 0));
            var withItem = dictionaries.saveItem("ORDER_STATUS", new SaveDictionaryItemRequest("PAID", "已支付", 1, true, 0));
            assertThat(withItem.items()).singleElement().satisfies(item -> assertThat(item.value()).isEqualTo("已支付"));
            var updatedItem = dictionaries.saveItem("ORDER_STATUS", new SaveDictionaryItemRequest("PAID", "支付完成", 2, true, withItem.items().getFirst().version()));
            assertThat(updatedItem.items().getFirst().value()).isEqualTo("支付完成");
            assertThat(dictionaries.saveType(new SaveDictionaryTypeRequest("ORDER_STATUS", "订单状态字典", true, type.version())).name()).isEqualTo("订单状态字典");
            assertThat(dictionaries.all()).hasSize(1);
            assertBusinessError(() -> dictionaries.saveItem("UNKNOWN", new SaveDictionaryItemRequest("X", "X", 0, true, 0)), ErrorCode.RESOURCE_NOT_FOUND);
            assertBusinessError(() -> dictionaries.saveType(new SaveDictionaryTypeRequest("ORDER_STATUS", "冲突", true, 999)), ErrorCode.BUSINESS_CONFLICT);

            var catalog = new SystemCatalogService(dataSource);
            assertThat(catalog.dictionaries()).singleElement().satisfies(d -> assertThat(d.items()).hasSize(1));
            var flag = catalog.updateFlag("ai.customer", new UpdateFeatureFlagRequest(true, 25, "{}", 0), 7);
            assertThat(catalog.flags()).containsExactly(flag);
            var changedFlag = catalog.updateFlag("ai.customer", new UpdateFeatureFlagRequest(false, 0, null, flag.version()), 7);
            assertThat(changedFlag.enabled()).isFalse();
            assertBusinessError(() -> catalog.updateFlag("INVALID", new UpdateFeatureFlagRequest(true, 1, null, 0), 7), ErrorCode.VALIDATION_ERROR);
            assertBusinessError(() -> catalog.updateFlag("ai.customer", new UpdateFeatureFlagRequest(true, 1, null, 999), 7), ErrorCode.BUSINESS_CONFLICT);

            var settings = new SystemSettingService(dataSource);
            var setting = settings.update("ai.prompt", new UpdateSystemSettingRequest("secret-one", "STRING", true, 0), 7);
            assertThat(setting.value()).isEqualTo("[REDACTED]");
            var updated = settings.update("ai.prompt", new UpdateSystemSettingRequest("public", "STRING", false, setting.version()), 7);
            assertThat(updated.value()).isEqualTo("public");
            assertThat(settings.list()).containsExactly(updated);
            assertBusinessError(() -> settings.update("INVALID", new UpdateSystemSettingRequest("x", "STRING", false, 0), 7), ErrorCode.VALIDATION_ERROR);
            assertBusinessError(() -> settings.update("ai.prompt", new UpdateSystemSettingRequest("x", "STRING", false, 999), 7), ErrorCode.BUSINESS_CONFLICT);

            var providers = new AiProviderConfigService(dataSource, new SystemSecretCipher(
                    "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)));
            assertThat(providers.view().apiKeyConfigured()).isFalse();
            var configured = providers.update(new UpdateAiProviderConfigRequest("DOUBAO_ARK",
                    "https://ark.cn-beijing.volces.com/api/v3", "doubao-chat-test",
                    "doubao-embedding-test", true, "ark-api-key-sensitive", 0), 7);
            assertThat(configured.apiKeyConfigured()).isTrue();
            assertThat(configured.webSearchEnabled()).isTrue();
            assertThat(configured.apiKeyMasked()).doesNotContain("sensitive");
            assertThat(providers.internal().apiKey()).isEqualTo("ark-api-key-sensitive");
            assertBusinessError(() -> providers.update(new UpdateAiProviderConfigRequest("DOUBAO_ARK",
                    "http://insecure.example", "doubao-chat-test", "doubao-embedding-test", true, null,
                    configured.version()), 7), ErrorCode.VALIDATION_ERROR);
        }
    }

    private static void assertBusinessError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.errorCode()).isEqualTo(expected));
    }
}
