package com.yuegang.zhihui.knowledge.api;

import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.web.TraceIdResolver;
import com.yuegang.zhihui.knowledge.application.KnowledgeLifecycleService;
import com.yuegang.zhihui.knowledge.security.KnowledgeUserContext;
import com.yuegang.zhihui.knowledge.security.KnowledgeUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class KnowledgeLifecycleController {
    private final KnowledgeLifecycleService service;
    private final KnowledgeUserResolver users;

    public KnowledgeLifecycleController(KnowledgeLifecycleService service, KnowledgeUserResolver users) {
        this.service = service;
        this.users = users;
    }

    @GetMapping("/api/v1/knowledge/documents")
    ApiResponse<List<KnowledgeDocumentView>> publicList(@RequestParam(required = false) String category,
                                                        @RequestParam(defaultValue = "20") int limit,
                                                        HttpServletRequest request) {
        KnowledgeUserContext user = users.resolveContext(request);
        return ok(service.list(null, category, true, limit, user.knowledgeVisibilities()), request);
    }

    @GetMapping("/api/v1/admin/knowledge/documents")
    ApiResponse<List<KnowledgeDocumentView>> adminList(@RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String category,
                                                       @RequestParam(defaultValue = "50") int limit,
                                                       HttpServletRequest request) {
        users.resolve(request, true);
        return ok(service.list(status, category, false, limit), request);
    }

    @PutMapping("/api/v1/admin/knowledge/documents/{id}/offline")
    ApiResponse<KnowledgeDocumentView> offline(@PathVariable String id, @RequestParam long version,
                                               @RequestParam(required = false) String reason,
                                               HttpServletRequest request) {
        return ok(service.offline(users.resolve(request, true), id, version, reason), request);
    }

    private static <T> ApiResponse<T> ok(T value, HttpServletRequest request) {
        return ApiResponse.success(value, TraceIdResolver.resolve(request));
    }
}
