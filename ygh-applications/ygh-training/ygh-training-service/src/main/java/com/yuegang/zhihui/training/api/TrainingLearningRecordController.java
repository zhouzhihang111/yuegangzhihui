package com.yuegang.zhihui.training.api;

import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.web.TraceIdResolver;
import com.yuegang.zhihui.training.application.TrainingDocumentProgressService;
import com.yuegang.zhihui.training.application.TrainingLearningRecordService;
import com.yuegang.zhihui.training.security.TrainingUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/training/learning")
public final class TrainingLearningRecordController {
    private final TrainingLearningRecordService service;
    private final TrainingDocumentProgressService documents;
    private final TrainingUserResolver users;

    public TrainingLearningRecordController(TrainingLearningRecordService service,
                                            TrainingDocumentProgressService documents,
                                            TrainingUserResolver users) {
        this.service = service;
        this.documents = documents;
        this.users = users;
    }

    @PutMapping("/position")
    ApiResponse<Map<String, Boolean>> position(@Valid @RequestBody ReadingPositionRequest body,
                                               HttpServletRequest request) {
        service.recordPosition(users.resolve(request), body);
        return ok(Map.of("recorded", true), request);
    }

    @GetMapping("/assignments/{id}/chapters")
    ApiResponse<List<ChapterProgressView>> progress(@PathVariable String id, HttpServletRequest request) {
        return ok(service.progress(users.resolve(request), id), request);
    }

    @GetMapping("/assignments/{id}/attempts")
    ApiResponse<List<QuizAttemptDetailView>> attempts(@PathVariable String id, HttpServletRequest request) {
        return ok(service.attempts(users.resolve(request), id), request);
    }

    @GetMapping("/assignments/{assignmentId}/documents")
    ApiResponse<List<DocumentProgressView>> documentProgress(@PathVariable String assignmentId,
                                                             @RequestParam String chapterId,
                                                             HttpServletRequest request) {
        return ok(documents.documents(users.resolve(request), assignmentId, chapterId), request);
    }

    @PostMapping("/assignments/{assignmentId}/documents/{documentId}/complete")
    ApiResponse<DocumentProgressView> completeDocument(@PathVariable String assignmentId,
                                                       @PathVariable String documentId,
                                                       HttpServletRequest request) {
        return ok(documents.complete(users.resolve(request), assignmentId, documentId), request);
    }

    @GetMapping("/admin/analytics")
    ApiResponse<TrainingAnalyticsView> analytics(HttpServletRequest request) {
        users.requirePermission(request, "training:statistics:read");
        return ok(service.analytics(), request);
    }

    @GetMapping("/admin/employee-progress")
    ApiResponse<List<EmployeeLearningProgressView>> employeeProgress(HttpServletRequest request) {
        users.requirePermission(request, "training:statistics:read");
        return ok(documents.employeeProgress(), request);
    }

    private static <T> ApiResponse<T> ok(T data, HttpServletRequest request) {
        return ApiResponse.success(data, TraceIdResolver.resolve(request));
    }
}
