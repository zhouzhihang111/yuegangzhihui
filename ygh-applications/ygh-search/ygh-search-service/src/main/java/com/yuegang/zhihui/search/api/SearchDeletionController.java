package com.yuegang.zhihui.search.api;

import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.web.TraceIdResolver;
import com.yuegang.zhihui.search.application.SearchDeletionService;
import com.yuegang.zhihui.search.security.SearchInternalSecurity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class SearchDeletionController {
    private final SearchDeletionService service;
    private final SearchInternalSecurity security;

    public SearchDeletionController(SearchDeletionService service, SearchInternalSecurity security) {
        this.service = service;
        this.security = security;
    }

    @PostMapping("/internal/v1/search/delete-document")
    ApiResponse<Map<String, Boolean>> delete(@Valid @RequestBody DeleteDocumentCommand command,
                                              HttpServletRequest request) {
        security.verify(request);
        service.deleteDocument(command.documentId(), command.indexName());
        return ApiResponse.success(Map.of("completed", true), TraceIdResolver.resolve(request));
    }
}
