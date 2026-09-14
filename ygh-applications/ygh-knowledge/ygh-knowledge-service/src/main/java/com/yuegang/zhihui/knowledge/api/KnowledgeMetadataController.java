package com.yuegang.zhihui.knowledge.api;

import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.web.TraceIdResolver;
import com.yuegang.zhihui.knowledge.application.KnowledgeAccessGuard;
import com.yuegang.zhihui.knowledge.application.KnowledgeMetadataService;
import com.yuegang.zhihui.knowledge.security.KnowledgeUserContext;
import com.yuegang.zhihui.knowledge.security.KnowledgeUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge/documents/{id}/metadata")
public final class KnowledgeMetadataController {
    private final KnowledgeMetadataService service;
    private final KnowledgeUserResolver users;
    private final KnowledgeAccessGuard access;

    public KnowledgeMetadataController(KnowledgeMetadataService service, KnowledgeUserResolver users,
                                       KnowledgeAccessGuard access) {
        this.service = service;
        this.users = users;
        this.access = access;
    }

    @GetMapping
    ApiResponse<KnowledgeMetadataView> get(@PathVariable String id, HttpServletRequest request) {
        KnowledgeUserContext user = users.resolveContext(request);
        long documentId = positive(id);
        if (!user.administrator()) access.requirePublished(documentId, user.knowledgeVisibilities());
        return ok(service.view(documentId), request);
    }

    @PutMapping
    ApiResponse<KnowledgeMetadataView> update(@PathVariable String id,
                                              @Valid @RequestBody UpdateKnowledgeMetadataRequest body,
                                              HttpServletRequest request) {
        return ok(service.update(users.resolve(request, true), id, body), request);
    }

    private static long positive(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException exception) {
            throw new com.yuegang.zhihui.common.core.BusinessException(
                    com.yuegang.zhihui.common.core.ErrorCode.VALIDATION_ERROR);
        }
    }

    private static <T> ApiResponse<T> ok(T value, HttpServletRequest request) {
        return ApiResponse.success(value, TraceIdResolver.resolve(request));
    }
}
