package com.yuegang.zhihui.training.api;

import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.web.TraceIdResolver;
import com.yuegang.zhihui.training.application.TrainingQuizService;
import com.yuegang.zhihui.training.security.TrainingUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/training/quizzes")
public final class TrainingQuizController {
    private final TrainingQuizService service;
    private final TrainingUserResolver users;
    public TrainingQuizController(TrainingQuizService service, TrainingUserResolver users){this.service=service;this.users=users;}
    @PostMapping("/attempts") ApiResponse<QuizAttemptView> submit(@Valid @RequestBody SubmitQuizRequest request, HttpServletRequest servletRequest){return ApiResponse.success(service.submit(users.resolve(servletRequest),request), TraceIdResolver.resolve(servletRequest));}
}
