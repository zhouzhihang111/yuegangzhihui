package com.yuegang.zhihui.ai.domain;

import java.util.List;

public interface ModelGateway {
    String answer(String systemPrompt, String userPrompt);

    default ModelAnswer answerWithSources(String systemPrompt, String userPrompt) {
        return new ModelAnswer(answer(systemPrompt, userPrompt), List.of());
    }

    default String modelName() { return "unknown"; }
    default boolean available() { return true; }
    default boolean supportsWebSearch() { return false; }
}
