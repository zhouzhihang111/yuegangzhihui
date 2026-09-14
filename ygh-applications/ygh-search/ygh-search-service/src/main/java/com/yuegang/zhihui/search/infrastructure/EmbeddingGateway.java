package com.yuegang.zhihui.search.infrastructure;

import java.util.List;

public interface EmbeddingGateway {
    List<Double> embed(String text);
    boolean configured();
}
