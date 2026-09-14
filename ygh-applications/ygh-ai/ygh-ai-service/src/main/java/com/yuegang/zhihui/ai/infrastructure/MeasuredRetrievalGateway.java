package com.yuegang.zhihui.ai.infrastructure;

import com.yuegang.zhihui.ai.domain.RetrievalGateway;
import com.yuegang.zhihui.search.api.SearchHit;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Set;

public final class MeasuredRetrievalGateway implements RetrievalGateway {
    private final RetrievalGateway delegate;
    private final Timer duration;
    private final DistributionSummary hits;

    public MeasuredRetrievalGateway(RetrievalGateway delegate, MeterRegistry metrics) {
        this.delegate = delegate;
        duration = Timer.builder("ygh.ai.retrieval.duration").register(metrics);
        hits = DistributionSummary.builder("ygh.ai.retrieval.hits").register(metrics);
    }

    @Override
    public List<SearchHit> search(String query, String category, int limit) {
        return search(query, category, limit, Set.of("PUBLIC"));
    }

    @Override
    public List<SearchHit> search(String query, String category, int limit, Set<String> visibilities) {
        return duration.record(() -> {
            List<SearchHit> result = delegate.search(query, category, limit, visibilities);
            hits.record(result.size());
            return result;
        });
    }
}
