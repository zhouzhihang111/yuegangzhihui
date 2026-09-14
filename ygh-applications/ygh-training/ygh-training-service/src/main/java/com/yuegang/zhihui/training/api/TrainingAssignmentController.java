package com.yuegang.zhihui.training.api;

import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.web.TraceIdResolver;
import com.yuegang.zhihui.training.application.TrainingAssignmentService;
import com.yuegang.zhihui.training.security.TrainingUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/training/assignments")
public final class TrainingAssignmentController {
    private final TrainingAssignmentService service; private final TrainingUserResolver users;
    public TrainingAssignmentController(TrainingAssignmentService service,TrainingUserResolver users){this.service=service;this.users=users;}
    @PostMapping ApiResponse<AssignmentView> assign(@Valid @RequestBody CreateAssignmentRequest request,HttpServletRequest servletRequest){return ApiResponse.success(service.assign(users.requirePermission(servletRequest,"training:assignment:create"),request),TraceIdResolver.resolve(servletRequest));}
    @GetMapping("/mine") ApiResponse<List<AssignmentView>> mine(HttpServletRequest servletRequest){return ApiResponse.success(service.mine(users.resolve(servletRequest)),TraceIdResolver.resolve(servletRequest));}
    @GetMapping("/statistics") ApiResponse<TrainingStatisticsView> statistics(HttpServletRequest servletRequest){users.requirePermission(servletRequest,"training:statistics:read");return ApiResponse.success(service.statistics(),TraceIdResolver.resolve(servletRequest));}
}
