package com.yuegang.zhihui.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuegang.zhihui.ai.domain.ModelGateway;
import com.yuegang.zhihui.ai.domain.RetrievalGateway;
import com.yuegang.zhihui.ai.infrastructure.CommerceToolGateway;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Base64;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class AiConfigurationTest {
    @Test
    void createsEveryApplicationBeanWithInjectedDependencies() {
        AiConfiguration configuration = new AiConfiguration();
        DataSource dataSource = mock(DataSource.class);
        ObjectMapper json = new ObjectMapper();
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        String secret = Base64.getEncoder().encodeToString(
                "01234567890123456789012345678901".getBytes());

        AiGovernanceService governance = configuration.aiGovernanceService(dataSource);
        AiSafetyPolicy policy = configuration.aiSafetyPolicy(dataSource);
        var providerConfigs = configuration.systemAiProviderConfigClient("http://localhost", secret);
        ModelGateway model = configuration.modelGateway(providerConfigs, governance, metrics);
        RetrievalGateway retrieval = configuration.retrievalGateway(
                "http://localhost", secret, policy, metrics);
        CommerceToolGateway tools = configuration.commerceToolGateway(
                "http://product", "http://inventory", "http://order", secret, json);
        ChatService chat = configuration.chatService(retrieval, model, tools, dataSource, json);

        assertThat(configuration.aiEvaluationService(dataSource, chat)).isNotNull();
        assertThat(configuration.conversationService(dataSource)).isNotNull();
        assertThat(configuration.aiUserResolver(secret)).isNotNull();
        assertThat(chat).isNotNull();
    }
}
