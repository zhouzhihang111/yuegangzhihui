package com.yuegang.zhihui.search.infrastructure;

import com.yuegang.zhihui.system.api.InternalAiProviderConfig;
import java.util.List;

public final class DynamicDoubaoEmbeddingGateway implements EmbeddingGateway {
    private final SystemAiProviderConfigClient configs;
    private volatile Cached cached;

    public DynamicDoubaoEmbeddingGateway(SystemAiProviderConfigClient configs) { this.configs = configs; }
    @Override public List<Double> embed(String text) { return delegate().embed(text); }
    @Override public boolean configured() { return delegate().configured(); }

    private DoubaoEmbeddingGateway delegate() {
        InternalAiProviderConfig config;
        try { config = configs.current(); }
        catch (RuntimeException unavailable) {
            Cached value = cached;
            return value == null ? new DoubaoEmbeddingGateway("https://localhost", "", "unavailable") : value.gateway();
        }
        Cached value = cached;
        if (value != null && value.version() == config.version()) return value.gateway();
        var gateway = new DoubaoEmbeddingGateway(config.baseUrl(), config.apiKey(), config.embeddingModel());
        cached = new Cached(config.version(), gateway);
        return gateway;
    }
    private record Cached(long version, DoubaoEmbeddingGateway gateway) {}
}
