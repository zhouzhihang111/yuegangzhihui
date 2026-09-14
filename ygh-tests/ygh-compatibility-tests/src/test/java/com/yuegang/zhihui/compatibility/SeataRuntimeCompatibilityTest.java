package com.yuegang.zhihui.compatibility;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.seata.core.model.GlobalStatus;
import org.apache.seata.core.rpc.netty.TmNettyRemotingClient;
import org.apache.seata.tm.api.GlobalTransactionContext;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class SeataRuntimeCompatibilityTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "YGH_SEATA_RUNTIME_TEST", matches = "true")
    void beginsAndCommitsARealGlobalTransactionOnJdk25() throws Exception {
        String server = System.getenv().getOrDefault("YGH_SEATA_SERVER", "127.0.0.1:8091");
        String group = "ygh_runtime_tx_group";
        try (var context = new SpringApplicationBuilder(ProbeApplication.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.application.name=ygh-seata-runtime-probe",
                        "spring.cloud.gateway.enabled=false",
                        "spring.cloud.stream.enabled=false",
                        "spring.cloud.nacos.discovery.enabled=false",
                        "spring.cloud.nacos.config.enabled=false",
                        "spring.cloud.nacos.config.import-check.enabled=false",
                        "seata.enabled=true",
                        "seata.enable-auto-data-source-proxy=false",
                        "seata.application-id=ygh-seata-runtime-probe",
                        "seata.tx-service-group=" + group,
                        "seata.registry.type=file",
                        "seata.config.type=file",
                        "seata.service.vgroup-mapping." + group + "=default",
                        "seata.service.grouplist.default=" + server)
                .run()) {
            assertThat(context.isActive()).isTrue();
            var transaction = GlobalTransactionContext.createNew();
            transaction.begin(15_000, "jdk25-runtime-probe");
            assertThat(transaction.getXid()).isNotBlank();
            assertThat(transaction.getStatus()).isEqualTo(GlobalStatus.Begin);
            transaction.commit();
            assertThat(transaction.getLocalStatus()).isEqualTo(GlobalStatus.Committed);
        } finally {
            TmNettyRemotingClient.getInstance().destroy();
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = {
        "org.springframework.cloud.gateway.config.GatewayAutoConfiguration",
        "org.springframework.cloud.gateway.config.GatewayFunctionAutoConfiguration",
        "org.springframework.cloud.gateway.config.GatewayStreamAutoConfiguration",
        "org.springframework.cloud.gateway.config.GatewayMetricsAutoConfiguration"
    })
    static class ProbeApplication {}
}
