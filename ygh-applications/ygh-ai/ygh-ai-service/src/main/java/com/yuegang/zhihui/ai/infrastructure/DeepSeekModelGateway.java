package com.yuegang.zhihui.ai.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuegang.zhihui.ai.domain.ModelAnswer;
import com.yuegang.zhihui.ai.domain.ModelGateway;
import com.yuegang.zhihui.ai.domain.ModelProviderException;
import com.yuegang.zhihui.ai.domain.ModelSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** DeepSeek Chat Completions API 适配器，兼容 OpenAI 接口格式。 */
public final class DeepSeekModelGateway implements ModelGateway {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final RestClient client;
    private final String modelName;
    private final String chatUrl;

    public DeepSeekModelGateway(String baseUrl, String apiKey, String modelName) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("DeepSeek key missing");
        this.modelName = modelName;
        chatUrl = trimTrailingSlash(baseUrl) + "/chat/completions";
        client = RestClient.builder()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    @Override
    public String answer(String system, String user) {
        return answerWithSources(system, user).text();
    }

    @Override
    public ModelAnswer answerWithSources(String system, String user) {
        Map<?, ?> response;
        try {
            List<Map<String, String>> messages = new ArrayList<>();
            if (system != null && !system.isBlank()) {
                messages.add(Map.of("role", "system", "content", system));
            }
            messages.add(Map.of("role", "user", "content", user));
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("model", modelName);
            request.put("messages", messages);
            request.put("stream", false);
            response = client.post().uri(chatUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve().body(Map.class);
        } catch (RestClientResponseException exception) {
            throw providerFailure(exception);
        }
        String text = extractText(response);
        if (text == null || text.isBlank()) throw new IllegalStateException("empty model response");
        return new ModelAnswer(text, extractSources(response));
    }

    @Override
    public String modelName() { return modelName; }

    @Override
    public boolean supportsWebSearch() { return false; }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("DeepSeek base URL missing");
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    @SuppressWarnings("unchecked")
    private static String extractText(Map<?, ?> response) {
        if (response == null) return null;
        Object choices = response.get("choices");
        if (!(choices instanceof List<?> choiceList) || choiceList.isEmpty()) return null;
        Object first = choiceList.get(0);
        if (!(first instanceof Map<?, ?> choice)) return null;
        Object message = choice.get("message");
        if (!(message instanceof Map<?, ?> msg)) return null;
        Object content = msg.get("content");
        return content instanceof String text && !text.isBlank() ? text : null;
    }

    private static List<ModelSource> extractSources(Map<?, ?> response) {
        if (response == null) return List.of();
        Map<String, ModelSource> sources = new LinkedHashMap<>();
        collectSources(JSON.valueToTree(response), sources);
        return new ArrayList<>(sources.values()).stream().limit(8).toList();
    }

    private static void collectSources(JsonNode node, Map<String, ModelSource> sources) {
        if (node == null || node.isNull() || sources.size() >= 8) return;
        if (node.isObject()) {
            String url = node.path("url").asText("");
            if ((url.startsWith("https://") || url.startsWith("http://")) && !sources.containsKey(url)) {
                String title = node.path("title").asText("模型来源");
                String excerpt = node.path("snippet").asText(node.path("text").asText("模型检索结果"));
                sources.put(url, new ModelSource(title.isBlank() ? "模型来源" : title,
                        excerpt.isBlank() ? "模型检索结果" : excerpt, url));
            }
            node.elements().forEachRemaining(child -> collectSources(child, sources));
        } else if (node.isArray()) {
            node.elements().forEachRemaining(child -> collectSources(child, sources));
        }
    }

    private static ModelProviderException providerFailure(RestClientResponseException exception) {
        String code = "UNKNOWN";
        String message = "未返回错误说明";
        try {
            JsonNode body = JSON.readTree(exception.getResponseBodyAsString());
            JsonNode error = body.path("error");
            if (error.isMissingNode() || error.isNull()) error = body;
            code = error.path("code").asText(code);
            message = error.path("message").asText(message);
        } catch (Exception ignored) {
            // 非 JSON 错误页只透传 HTTP 状态，不记录响应正文。
        }
        return new ModelProviderException(exception.getStatusCode().value(), code, message, exception);
    }
}
