package com.yuegang.zhihui.ai.infrastructure;

import com.yuegang.zhihui.ai.domain.ModelGateway;

/** Fail-closed development adapter used when no paid model credential is injected. */
public final class UnavailableModelGateway implements ModelGateway {
    @Override public String answer(String systemPrompt, String userPrompt) {
        return "当前 AI 模型未配置，无法生成可靠回答。请联系管理员配置豆包模型后重试。";
    }
    @Override public String modelName() { return "unavailable"; }
    @Override public boolean available() { return false; }
}
