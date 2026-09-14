package com.yuegang.zhihui.knowledge.api;

import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.web.TraceIdResolver;
import com.yuegang.zhihui.knowledge.application.KnowledgeAccessGuard;
import com.yuegang.zhihui.knowledge.application.KnowledgeDocumentService;
import com.yuegang.zhihui.knowledge.security.KnowledgeUserContext;
import com.yuegang.zhihui.knowledge.security.KnowledgeUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/knowledge/documents")
public final class KnowledgeController {
    private final KnowledgeDocumentService service;
    private final KnowledgeUserResolver users;
    private final KnowledgeAccessGuard access;

    public KnowledgeController(KnowledgeDocumentService service, KnowledgeUserResolver users,
                               KnowledgeAccessGuard access) {
        this.service = service;
        this.users = users;
        this.access = access;
    }

    @PostMapping(consumes = "multipart/form-data")
    ApiResponse<KnowledgeDocumentView> upload(@RequestParam String title, @RequestParam String category,
                                              @org.springframework.web.bind.annotation.RequestPart MultipartFile file,
                                              HttpServletRequest request) {
        return ok(service.upload(users.resolve(request, true), title, category, file), request);
    }

    @PostMapping("/{id}/review")
    ApiResponse<KnowledgeDocumentView> review(@PathVariable String id,
                                              @Valid @RequestBody ReviewKnowledgeRequest body,
                                              HttpServletRequest request) {
        return ok(service.review(users.resolve(request, true), id, body), request);
    }

    @GetMapping("/{id}")
    ApiResponse<KnowledgeDocumentView> get(@PathVariable String id, HttpServletRequest request) {
        KnowledgeUserContext user = users.resolveContext(request);
        long document = positive(id);
        if (!user.administrator()) access.requirePublished(document, user.knowledgeVisibilities());
        return ok(service.get(document), request);
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
