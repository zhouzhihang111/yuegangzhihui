package com.yuegang.zhihui.ai.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DoubaoModelGatewayWebSearchTest {
    @Test
    void sendsWebSearchToolAndPreservesPublicSource() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v3/responses", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = """
                    {"output":[{"type":"message","content":[{
                      "type":"output_text","text":"需要提交食品标签等材料。",
                      "annotations":[{"type":"url_citation","title":"海关总署",
                        "url":"https://www.customs.gov.cn/example","snippet":"进口食品监管要求"}]
                    }]}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/api/v3";
            var gateway = new DoubaoModelGateway(baseUrl, "test-key", "test-model", true);

            var answer = gateway.answerWithSources("system", "进口零食需要什么材料？");

            assertThat(requestBody.get()).contains("\"tools\":[{\"type\":\"web_search\"}]");
            assertThat(answer.text()).isEqualTo("需要提交食品标签等材料。");
            assertThat(answer.sources()).singleElement().satisfies(source -> {
                assertThat(source.title()).isEqualTo("海关总署");
                assertThat(source.url()).isEqualTo("https://www.customs.gov.cn/example");
                assertThat(source.excerpt()).isEqualTo("进口食品监管要求");
            });
        } finally {
            server.stop(0);
        }
    }
}
