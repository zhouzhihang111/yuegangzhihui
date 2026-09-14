package com.yuegang.zhihui.training.api;

import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.web.TraceIdResolver;
import com.yuegang.zhihui.training.application.TrainingAccessGuard;
import com.yuegang.zhihui.training.application.TrainingCatalogQueryService;
import com.yuegang.zhihui.training.security.TrainingUserContext;
import com.yuegang.zhihui.training.security.TrainingUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/training")
public final class TrainingCatalogQueryController {
    private final TrainingCatalogQueryService service;
    private final TrainingUserResolver users;
    private final TrainingAccessGuard access;

    public TrainingCatalogQueryController(TrainingCatalogQueryService service, TrainingUserResolver users,
                                          TrainingAccessGuard access) {
        this.service = service;
        this.users = users;
        this.access = access;
    }

    @GetMapping("/courses/{id}/gates")
    ApiResponse<List<GateView>> gates(@PathVariable String id, HttpServletRequest request) {
        TrainingUserContext user = users.context(request);
        access.requireCourse(user, id);
        return ApiResponse.success(service.gates(id), TraceIdResolver.resolve(request));
    }

    @GetMapping("/chapters/{id}/documents")
    ApiResponse<List<TrainingDocumentView>> documents(@PathVariable String id, HttpServletRequest request) {
        TrainingUserContext user = users.context(request);
        access.requireChapter(user, id);
        return ApiResponse.success(service.documents(id), TraceIdResolver.resolve(request));
    }
}
