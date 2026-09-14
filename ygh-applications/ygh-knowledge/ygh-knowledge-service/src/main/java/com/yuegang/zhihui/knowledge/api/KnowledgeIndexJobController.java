package com.yuegang.zhihui.knowledge.api;

import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.web.TraceIdResolver;
import com.yuegang.zhihui.knowledge.application.KnowledgeIndexJobService;
import com.yuegang.zhihui.knowledge.security.KnowledgeUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/knowledge/index-jobs")
public final class KnowledgeIndexJobController {
    private final KnowledgeIndexJobService service;
    private final KnowledgeUserResolver users;

    public KnowledgeIndexJobController(KnowledgeIndexJobService service, KnowledgeUserResolver users) {
        this.service = service;
        this.users = users;
    }

    @GetMapping
    ApiResponse<List<KnowledgeIndexJobView>> list(@RequestParam(required = false) String documentId,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(defaultValue = "50") int limit,
                                                   HttpServletRequest request) {
        users.resolve(request, true);
        return ApiResponse.success(service.list(documentId, status, limit), TraceIdResolver.resolve(request));
    }

    @PostMapping("/{id}/retry")
    ApiResponse<KnowledgeIndexJobView> retry(@PathVariable String id, HttpServletRequest request) {
        users.resolve(request, true);
        return ApiResponse.success(service.retry(id), TraceIdResolver.resolve(request));
    }

    @PostMapping("/rebuild")
    ApiResponse<RebuildKnowledgeIndexResponse> rebuild(
            @jakarta.validation.Valid @RequestBody RebuildKnowledgeIndexRequest command,
            HttpServletRequest request) {
        users.resolve(request, true);
        return ApiResponse.success(service.rebuild(command.version()), TraceIdResolver.resolve(request));
    }
}
