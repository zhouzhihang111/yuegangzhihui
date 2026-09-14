package com.yuegang.zhihui.training.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.common.test.YghTestContainerFactory;
import com.yuegang.zhihui.training.api.*;
import com.yuegang.zhihui.training.security.TrainingUserContext;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;

class TrainingServicesIntegrationTest {
    @Test
    void completesPublishedCourseLearningAndQuizJourney() throws Exception {
        try (var mysql = YghTestContainerFactory.mysql().start()) {
            Flyway.configure().dataSource(mysql.jdbcUrl(), mysql.username(), mysql.credential())
                    .locations("classpath:db/migration").load().migrate();
            var dataSource = new DriverManagerDataSource(
                    mysql.jdbcUrl(), mysql.username(), mysql.credential());
            var json = new ObjectMapper();
            var content = new TrainingContentService(
                    dataSource, json, Files.createTempDirectory("training-content").toString());

            CourseView course = content.createCourse(
                    new SaveCourseRequest("跨境通关基础", "岗位必修", 30, 80));
            ChapterView chapter = content.createChapter(
                    new SaveChapterRequest(course.id(), "申报准备", 1, 30));
            assertBusinessError(() -> content.createChapter(
                    new SaveChapterRequest(course.id(), "重复顺序", 1, 30)), ErrorCode.BUSINESS_CONFLICT);
            GateView gate = content.createGate(
                    new SaveGateRequest(chapter.id(), "通关测验", 80, 3));
            assertBusinessError(() -> content.createGate(
                    new SaveGateRequest(chapter.id(), "重复关卡", 80, 2)), ErrorCode.BUSINESS_CONFLICT);
            QuestionView question = content.createQuestion(new SaveQuestionRequest(
                    gate.id(), "SINGLE", "申报前应核对什么？", List.of("资料", "忽略"),
                    "资料", "核对申报资料", 100));
            QuestionView keyedQuestion = content.createQuestion(new SaveQuestionRequest(
                    gate.id(), "SINGLE", "请选择 A", List.of("A 选项", "B 选项"),
                    "A", null, 1));
            content.createQuestion(new SaveQuestionRequest(
                    gate.id(), "TEXT", "补充说明", null, "完成", null, 1));
            assertThat(content.questions(gate.id())).hasSize(3)
                    .extracting(QuestionView::stem)
                    .containsExactlyInAnyOrder("申报前应核对什么？", "请选择 A", "补充说明");
            assertThat(content.chapters(course.id())).hasSize(1);
            assertThat(content.courses(true)).isEmpty();
            assertThat(content.courses(false)).hasSize(1);
            TrainingDocumentView document = content.upload(chapter.id(), new MockMultipartFile(
                    "file", "lesson.txt", "text/plain", "training material".getBytes()));
            assertThat(document.mediaType()).isEqualTo("text/plain");
            assertThatThrownBy(() -> content.upload(chapter.id(), null))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> content.upload(chapter.id(), new MockMultipartFile(
                    "file", "empty.txt", "text/plain", new byte[0])))
                    .isInstanceOf(BusinessException.class);

            var catalog = new TrainingCatalogQueryService(dataSource);
            assertThat(catalog.gates(course.id())).singleElement()
                    .extracting(GateView::id).isEqualTo(gate.id());
            assertThat(catalog.documents(chapter.id())).singleElement()
                    .extracting(TrainingDocumentView::fileName).isEqualTo("lesson.txt");
            assertThatThrownBy(() -> catalog.gates("0"))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> catalog.documents("not-an-id"))
                    .isInstanceOf(BusinessException.class);

            var paths = new LearningPathService(dataSource);
            LearningPathView path = paths.create(new SaveLearningPathRequest("CUSTOMS", "关务学习路径"));
            assertThat(paths.addCourse(path.id(), new AddPathCourseRequest(course.id(), 1, null)).courses())
                    .hasSize(1);
            assertThat(paths.byPosition("CUSTOMS")).hasSize(1);
            assertThat(paths.all()).hasSize(1);

            CourseView published = content.publish(course.id(), course.version());
            assertThat(published.status()).isEqualTo("PUBLISHED");
            assertThat(content.courses(true)).hasSize(1);
            assertThatThrownBy(() -> content.publish(course.id(), course.version()))
                    .isInstanceOf(BusinessException.class);

            var assignments = new TrainingAssignmentService(dataSource, json);
            var records = new TrainingLearningRecordService(dataSource, json);
            assertThat(records.analytics().assigned()).isZero();
            AssignmentView assignment = assignments.assign(9, new CreateAssignmentRequest(
                    "42", path.id(), course.id(), OffsetDateTime.now().plusDays(7)));
            assertThat(assignments.assign(9, new CreateAssignmentRequest(
                    "42", path.id(), course.id(), OffsetDateTime.now().plusDays(9))).assignmentId())
                    .isEqualTo(assignment.assignmentId());
            var access = new TrainingAccessGuard(dataSource);
            var learner = new TrainingUserContext(42, Set.of("EMPLOYEE"), Set.of());
            access.requireCourse(learner, course.id());
            access.requireChapter(learner, chapter.id());
            access.requireGate(learner, gate.id());
            assertThat(access.assignedCourseIds(learner)).containsExactly(course.id());
            assertThatThrownBy(() -> access.requireCourse(
                    new TrainingUserContext(7, Set.of("EMPLOYEE"), Set.of()), course.id()))
                    .isInstanceOf(BusinessException.class);
            access.requireCourse(new TrainingUserContext(9, Set.of("ADMIN"), Set.of()), course.id());
            assertThat(assignments.mine(42)).singleElement()
                    .extracting(AssignmentView::assignmentId).isEqualTo(assignment.assignmentId());
            assertThat(assignments.statistics().assigned()).isEqualTo(1);

            var progress = new TrainingProgressService(dataSource);
            assertThat(progress.get(42, assignment.assignmentId()).progressPercent()).isZero();
            ProgressView learned = progress.heartbeat(42, new LearningHeartbeat(
                    assignment.assignmentId(), chapter.id(), 30, "heartbeat-1"));
            assertThat(learned.progressPercent()).isZero();
            assertThat(progress.heartbeat(42, new LearningHeartbeat(
                    assignment.assignmentId(), chapter.id(), 30, "heartbeat-1")).progressPercent())
                    .isZero();

            var documentProgress = new TrainingDocumentProgressService(dataSource);
            assertThat(documentProgress.documents(42, assignment.assignmentId(), chapter.id()))
                    .singleElement().extracting(DocumentProgressView::status).isEqualTo("NOT_STARTED");
            assertThatThrownBy(() -> documentProgress.complete(42, assignment.assignmentId(), document.id()))
                    .isInstanceOf(BusinessException.class);
            documentProgress.recordOpened(42, assignment.assignmentId(), document.id());
            assertThat(documentProgress.complete(42, assignment.assignmentId(), document.id()).status())
                    .isEqualTo("COMPLETED");
            assertThat(progress.get(42, assignment.assignmentId()).progressPercent()).isEqualByComparingTo("100.00");
            assertThat(documentProgress.employeeProgress()).singleElement().satisfies(item -> {
                assertThat(item.completedDocuments()).isEqualTo(1);
                assertThat(item.totalDocuments()).isEqualTo(1);
            });

            records.recordPosition(42, new ReadingPositionRequest(
                    assignment.assignmentId(), chapter.id(), "page=2", "position-1"));
            records.recordPosition(42, new ReadingPositionRequest(
                    assignment.assignmentId(), chapter.id(), "page=9", "position-1"));
            assertThat(records.progress(42, assignment.assignmentId())).singleElement()
                    .extracting(ChapterProgressView::lastPosition).isEqualTo("page=2");

            var quizzes = new TrainingQuizService(dataSource, json);
            SubmitQuizRequest wrongSubmission = new SubmitQuizRequest(
                    assignment.assignmentId(), gate.id(), Map.of(question.id(), "忽略"), "quiz-request-wrong");
            assertThat(quizzes.submit(42, wrongSubmission).passed()).isFalse();
            SubmitQuizRequest optionTextSubmission = new SubmitQuizRequest(
                    assignment.assignmentId(), gate.id(), Map.of(keyedQuestion.id(), "A 选项"), "quiz-request-option-text");
            assertThat(quizzes.submit(42, optionTextSubmission).score()).isPositive();
            SubmitQuizRequest submission = new SubmitQuizRequest(
                    assignment.assignmentId(), gate.id(), Map.of(question.id(), "资料"), "quiz-request-1");
            QuizAttemptView attempt = quizzes.submit(42, submission);
            assertThat(attempt.passed()).isTrue();
            assertThat(quizzes.submit(42, submission).attemptId()).isEqualTo(attempt.attemptId());
            assertThat(records.attempts(42, assignment.assignmentId())).hasSize(3);
            assertThatThrownBy(() -> quizzes.submit(42, new SubmitQuizRequest(
                    assignment.assignmentId(), gate.id(), Map.of(), "quiz-request-over-limit")))
                    .isInstanceOf(BusinessException.class);
            assertThat(records.analytics().completed()).isEqualTo(1);
            assertThat(assignments.statistics().completed()).isEqualTo(1);

            assertThatThrownBy(() -> progress.get(7, assignment.assignmentId()))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> paths.addCourse("bad", new AddPathCourseRequest(course.id(), 1, null)))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> records.progress(7, assignment.assignmentId()))
                    .isInstanceOf(BusinessException.class);
        }
    }

    private static void assertBusinessError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.errorCode()).isEqualTo(expected));
    }
}
