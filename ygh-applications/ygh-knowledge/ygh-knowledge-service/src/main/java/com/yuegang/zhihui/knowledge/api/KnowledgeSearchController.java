package com.yuegang.zhihui.knowledge.api;

import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.web.TraceIdResolver;
import com.yuegang.zhihui.knowledge.application.KnowledgeSearchGateway;
import com.yuegang.zhihui.knowledge.security.KnowledgeUserContext;
import com.yuegang.zhihui.knowledge.security.KnowledgeUserResolver;
import com.yuegang.zhihui.search.api.SearchHit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class KnowledgeSearchController {
    private final KnowledgeSearchGateway search;
    private final KnowledgeUserResolver users;

    public KnowledgeSearchController(KnowledgeSearchGateway search, KnowledgeUserResolver users) {
        this.search = search;
        this.users = users;
    }

    @GetMapping("/api/v1/knowledge/search")
    ApiResponse<List<SearchHit>> search(@RequestParam @NotBlank @Size(max = 500) String query,
                                        @RequestParam(required = false) String category,
                                        @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit,
                                        HttpServletRequest request) {
        KnowledgeUserContext user = users.resolveContext(request);
        return ApiResponse.success(search.search(query, category, limit, user.knowledgeVisibilities()),
                TraceIdResolver.resolve(request));
    }
}
