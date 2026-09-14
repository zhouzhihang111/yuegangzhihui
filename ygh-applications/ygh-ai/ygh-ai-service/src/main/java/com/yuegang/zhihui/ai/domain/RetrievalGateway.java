package com.yuegang.zhihui.ai.domain;

import com.yuegang.zhihui.search.api.SearchHit;
import java.util.List;
import java.util.Set;

public interface RetrievalGateway {
    List<SearchHit> search(String query, String category, int limit);

    default List<SearchHit> search(String query, String category, int limit, Set<String> visibilities) {
        return search(query, category, limit);
    }
}
