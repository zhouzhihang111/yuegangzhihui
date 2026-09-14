package com.yuegang.zhihui.training.api;

import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.web.TraceIdResolver;
import com.yuegang.zhihui.training.application.TrainingAccessGuard;
import com.yuegang.zhihui.training.application.TrainingContentService;
import com.yuegang.zhihui.training.security.TrainingUserContext;
import com.yuegang.zhihui.training.security.TrainingUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/training")
public final class TrainingContentController {
    private final TrainingContentService service;
    private final TrainingUserResolver users;
    private final TrainingAccessGuard access;

    public TrainingContentController(TrainingContentService service, TrainingUserResolver users,
                                     TrainingAccessGuard access) {
        this.service = service;
        this.users = users;
        this.access = access;
    }

    @GetMapping("/courses")
    ApiResponse<List<CourseView>> courses(HttpServletRequest request) {
        TrainingUserContext user = users.context(request);
        List<CourseView> courses = service.courses(!user.courseManager());
        if (!user.courseManager()) {
            var allowed = access.assignedCourseIds(user);
            courses = courses.stream().filter(course -> allowed.contains(course.id())).toList();
        }
        return ok(courses, request);
    }

    @GetMapping("/courses/{id}/chapters")
    ApiResponse<List<ChapterView>> chapters(@PathVariable String id, HttpServletRequest request) {
        access.requireCourse(users.context(request), id);
        return ok(service.chapters(id), request);
    }

    @GetMapping("/gates/{id}/questions")
    ApiResponse<List<QuestionView>> questions(@PathVariable String id, HttpServletRequest request) {
        TrainingUserContext user = users.context(request);
        access.requireGate(user, id);
        List<QuestionView> questions = service.questions(id);
        if (!user.courseManager()) {
            questions = questions.stream().map(question -> new QuestionView(question.id(), question.gateId(),
                    question.type(), question.stem(), question.options(), null, question.score())).toList();
        }
        return ok(questions, request);
    }

    @PostMapping("/admin/courses")
    ApiResponse<CourseView> course(@Valid @RequestBody SaveCourseRequest body, HttpServletRequest request) {
        users.requirePermission(request, "training:course:write");
        return ok(service.createCourse(body), request);
    }

    @PutMapping("/admin/courses/{id}/publish")
    ApiResponse<CourseView> publish(@PathVariable String id, @RequestParam long version,
                                    HttpServletRequest request) {
        users.requirePermission(request, "training:course:publish");
        return ok(service.publish(id, version), request);
    }

    @PostMapping("/admin/chapters")
    ApiResponse<ChapterView> chapter(@Valid @RequestBody SaveChapterRequest body, HttpServletRequest request) {
        users.requirePermission(request, "training:course:write");
        return ok(service.createChapter(body), request);
    }

    @PostMapping("/admin/gates")
    ApiResponse<GateView> gate(@Valid @RequestBody SaveGateRequest body, HttpServletRequest request) {
        users.requirePermission(request, "training:course:write");
        return ok(service.createGate(body), request);
    }

    @PostMapping("/admin/questions")
    ApiResponse<QuestionView> question(@Valid @RequestBody SaveQuestionRequest body, HttpServletRequest request) {
        users.requirePermission(request, "training:course:write");
        return ok(service.createQuestion(body), request);
    }

    @PostMapping(value = "/admin/chapters/{id}/documents", consumes = "multipart/form-data")
    ApiResponse<TrainingDocumentView> document(@PathVariable String id, @RequestPart MultipartFile file,
                                                HttpServletRequest request) {
        users.requirePermission(request, "training:course:write");
        return ok(service.upload(id, file), request);
    }

    private static <T> ApiResponse<T> ok(T value, HttpServletRequest request) {
        return ApiResponse.success(value, TraceIdResolver.resolve(request));
    }
}
