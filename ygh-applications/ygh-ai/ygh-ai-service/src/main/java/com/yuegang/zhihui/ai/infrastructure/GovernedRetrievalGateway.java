package com.yuegang.zhihui.ai.infrastructure;

import com.yuegang.zhihui.ai.application.AiSafetyPolicy;
import com.yuegang.zhihui.ai.domain.RetrievalGateway;
import com.yuegang.zhihui.search.api.SearchHit;
import java.util.List;
import java.util.Set;

public final class GovernedRetrievalGateway implements RetrievalGateway {
    private final RetrievalGateway delegate;
    private final AiSafetyPolicy policy;

    public GovernedRetrievalGateway(RetrievalGateway delegate, AiSafetyPolicy policy) {
        this.delegate = delegate;
        this.policy = policy;
    }

    @Override
    public List<SearchHit> search(String query, String category, int limit) {
        return search(query, category, limit, Set.of("PUBLIC"));
    }

    @Override
    public List<SearchHit> search(String query, String category, int limit, Set<String> visibilities) {
        policy.guard(query, category);
        return delegate.search(query, category, limit, visibilities);
    }
}
