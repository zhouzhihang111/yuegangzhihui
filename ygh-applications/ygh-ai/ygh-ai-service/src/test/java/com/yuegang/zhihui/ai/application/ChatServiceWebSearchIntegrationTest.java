package com.yuegang.zhihui.ai.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuegang.zhihui.ai.api.ChatRequest;
import com.yuegang.zhihui.ai.domain.ModelAnswer;
import com.yuegang.zhihui.ai.domain.ModelGateway;
import com.yuegang.zhihui.ai.domain.ModelSource;
import com.yuegang.zhihui.ai.infrastructure.CommerceToolGateway;
import com.yuegang.zhihui.common.test.YghTestContainerFactory;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ChatServiceWebSearchIntegrationTest {
    @Test
    void answersProfessionalQuestionFromAuditableWebSourceWhenLocalKnowledgeIsEmpty() {
        try (var mysql = YghTestContainerFactory.mysql().start()) {
            Flyway.configure().dataSource(mysql.jdbcUrl(), mysql.username(), mysql.credential())
                    .locations("classpath:db/migration").load().migrate();
            var dataSource = new DriverManagerDataSource(
                    mysql.jdbcUrl(), mysql.username(), mysql.credential());
            ModelGateway webModel = new ModelGateway() {
                @Override public String answer(String systemPrompt, String userPrompt) { return "联网回答"; }
                @Override public ModelAnswer answerWithSources(String systemPrompt, String userPrompt) {
                    return new ModelAnswer("联网回答", List.of(new ModelSource(
                            "海关总署", "进口食品监管要求", "https://www.customs.gov.cn/example")));
                }
                @Override public boolean supportsWebSearch() { return true; }
            };
            var tools = new CommerceToolGateway("http://localhost:1", "http://localhost:1",
                    "01234567890123456789012345678901".getBytes(), new ObjectMapper());
            var service = new ChatService((query, category, limit) -> List.of(), webModel, tools, dataSource);

            var response = service.chat(7, new ChatRequest(
                    null, "进口零食需要哪些通关材料？", null, false));

            assertThat(response.refused()).isFalse();
            assertThat(response.answer()).isEqualTo("联网回答");
            assertThat(response.citations()).singleElement().satisfies(citation -> {
                assertThat(citation.sourceType()).isEqualTo("WEB");
                assertThat(citation.url()).isEqualTo("https://www.customs.gov.cn/example");
            });
        }
    }
}
