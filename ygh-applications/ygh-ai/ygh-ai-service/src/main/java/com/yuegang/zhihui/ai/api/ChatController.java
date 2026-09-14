package com.yuegang.zhihui.ai.api;

import com.yuegang.zhihui.ai.application.ChatService;
import com.yuegang.zhihui.ai.domain.ModelProviderException;
import com.yuegang.zhihui.ai.security.AiUserContext;
import com.yuegang.zhihui.ai.security.AiUserResolver;
import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.web.TraceIdResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/ai/chat")
public final class ChatController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatController.class);
    private final ChatService service;
    private final AiUserResolver users;

    public ChatController(ChatService service, AiUserResolver users) {
        this.service = service;
        this.users = users;
    }

    @PostMapping
    ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatRequest body, HttpServletRequest request) {
        AiUserContext user = users.resolveContext(request);
        return ApiResponse.success(service.chat(user.userId(), user.knowledgeVisibilities(), body),
                TraceIdResolver.resolve(request));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(@Valid @RequestBody ChatRequest body, HttpServletRequest request) {
        AiUserContext user = users.resolveContext(request);
        var emitter = new SseEmitter(120_000L);
        Thread.ofVirtual().name("ai-sse-").start(() -> {
            try {
                ChatResponse response = service.chat(user.userId(), user.knowledgeVisibilities(), body);
                emitter.send(SseEmitter.event().name("meta").data(Map.of(
                        "conversationId", response.conversationId(), "messageId", response.messageId())));
                for (String token : response.answer().split("(?<=\\G.{24})")) {
                    emitter.send(SseEmitter.event().name("delta").data(token));
                }
                emitter.send(SseEmitter.event().name("citations").data(response.citations()));
                emitter.send(SseEmitter.event().name("done").data(Map.of("refused", response.refused())));
                emitter.complete();
            } catch (Exception exception) {
                String errorCode = "AI_RESPONSE_UNAVAILABLE";
                String userMessage = "AI 模型暂不可用，请在后台检查对话模型或 Endpoint ID";
                if (exception instanceof ModelProviderException provider) {
                    errorCode = provider.providerCode();
                    userMessage = provider.userMessage();
                    LOGGER.warn("ai_stream_failed type={} status={} providerCode={} providerMessage={}",
                            exception.getClass().getSimpleName(), provider.httpStatus(), provider.providerCode(),
                            provider.providerMessage());
                } else {
                    LOGGER.warn("ai_stream_failed type={}", exception.getClass().getSimpleName());
                }
                try {
                    emitter.send(SseEmitter.event().name("error").data(Map.of(
                            "code", errorCode,
                            "message", userMessage)));
                    emitter.complete();
                } catch (Exception sendException) {
                    LOGGER.debug("ai_stream_error_event_failed type={}",
                            sendException.getClass().getSimpleName());
                    emitter.complete();
                }
            }
        });
        return emitter;
    }
}
