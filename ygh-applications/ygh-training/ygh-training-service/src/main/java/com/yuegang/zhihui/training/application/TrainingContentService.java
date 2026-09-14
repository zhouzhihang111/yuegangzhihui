package com.yuegang.zhihui.training.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.training.api.ChapterView;
import com.yuegang.zhihui.training.api.CourseView;
import com.yuegang.zhihui.training.api.GateView;
import com.yuegang.zhihui.training.api.QuestionView;
import com.yuegang.zhihui.training.api.SaveChapterRequest;
import com.yuegang.zhihui.training.api.SaveCourseRequest;
import com.yuegang.zhihui.training.api.SaveGateRequest;
import com.yuegang.zhihui.training.api.SaveQuestionRequest;
import com.yuegang.zhihui.training.api.TrainingDocumentView;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.tika.Tika;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.multipart.MultipartFile;

public final class TrainingContentService {
    private static final Set<String> ALLOWED = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain",
            "text/markdown");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Path root;
    private final Tika tika = new Tika();

    public TrainingContentService(DataSource dataSource, ObjectMapper objectMapper, String storage) {
        jdbc = new JdbcTemplate(dataSource);
        json = objectMapper;
        root = Path.of(storage).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalStateException("training storage unavailable", exception);
        }
    }

    public CourseView createCourse(SaveCourseRequest request) {
        long id = next();
        jdbc.update(
                "INSERT INTO training_course(id,title,description,pass_score,estimated_minutes) VALUES(?,?,?,?,?)",
                id, request.title(), request.description(), request.passScore(), request.estimatedMinutes());
        return course(id);
    }

    public CourseView publish(String course, long version) {
        long id = id(course);
        if (jdbc.update(
                "UPDATE training_course SET status='PUBLISHED',version=version+1 WHERE id=? AND version=? AND status='DRAFT'",
                id, version) != 1) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT);
        }
        return course(id);
    }

    public List<CourseView> courses(boolean publishedOnly) {
        return jdbc.query(
                "SELECT * FROM training_course" + (publishedOnly ? " WHERE status='PUBLISHED'" : "")
                        + " ORDER BY updated_at DESC",
                (row, index) -> mapCourse(row));
    }

    public ChapterView createChapter(SaveChapterRequest request) {
        long id = next();
        try {
            jdbc.update(
                    "INSERT INTO training_chapter(id,course_id,title,sequence_no,minimum_active_seconds) VALUES(?,?,?,?,?)",
                    id, id(request.courseId()), request.title(), request.sequenceNo(), request.minimumActiveSeconds());
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT);
        }
        return chapter(id);
    }

    public List<ChapterView> chapters(String course) {
        return jdbc.query(
                "SELECT * FROM training_chapter WHERE course_id=? ORDER BY sequence_no",
                (row, index) -> mapChapter(row), id(course));
    }

    public GateView createGate(SaveGateRequest request) {
        long id = next();
        try {
            jdbc.update(
                    "INSERT INTO training_gate(id,chapter_id,title,pass_score,maximum_attempts) VALUES(?,?,?,?,?)",
                    id, id(request.chapterId()), request.title(), request.passScore(), request.maximumAttempts());
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT);
        }
        return new GateView(Long.toString(id), request.chapterId(), request.title(), request.passScore(),
                request.maximumAttempts());
    }

    public QuestionView createQuestion(SaveQuestionRequest request) {
        long id = next();
        String options = write(request.options() == null ? List.of() : request.options());
        jdbc.update(
                "INSERT INTO training_question(id,gate_id,question_type,stem,options_json,correct_answer_hash,explanation,score) VALUES(?,?,?,?,?,?,?,?)",
                id, id(request.gateId()), request.type(), request.stem(), options, hash(request.correctAnswer()),
                request.explanation(), request.score());
        return new QuestionView(Long.toString(id), request.gateId(), request.type(), request.stem(), request.options(),
                request.explanation(), request.score());
    }

    public List<QuestionView> questions(String gate) {
        return jdbc.query(
                "SELECT * FROM training_question WHERE gate_id=? ORDER BY id",
                (row, index) -> new QuestionView(Long.toString(row.getLong("id")),
                        Long.toString(row.getLong("gate_id")), row.getString("question_type"),
                        row.getString("stem"), read(row.getString("options_json")),
                        row.getString("explanation"), row.getInt("score")),
                id(gate));
    }

    public TrainingDocumentView upload(String chapter, MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 50L * 1024 * 1024) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        long chapterId = id(chapter);
        long documentId = next();
        String original = Path.of(Objects.requireNonNullElse(file.getOriginalFilename(), "document"))
                .getFileName().toString();
        try {
            byte[] bytes = file.getBytes();
            String media = tika.detect(bytes, original);
            if (!ALLOWED.contains(media)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            Path target = root.resolve(documentId + "-" + sha.substring(0, 16)).normalize();
            if (!target.startsWith(root)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
            jdbc.update(
                    "INSERT INTO training_document(id,chapter_id,file_name,media_type,storage_key,sha256,size_bytes) VALUES(?,?,?,?,?,?,?)",
                    documentId, chapterId, original, media, target.getFileName().toString(), sha, bytes.length);
            return new TrainingDocumentView(Long.toString(documentId), chapter, original, media, bytes.length, "ACTIVE");
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private CourseView course(long id) {
        return jdbc.query("SELECT * FROM training_course WHERE id=?", row -> {
            if (!row.next()) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            return mapCourse(row);
        }, id);
    }

    private ChapterView chapter(long id) {
        return jdbc.query("SELECT * FROM training_chapter WHERE id=?", row -> {
            if (!row.next()) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            return mapChapter(row);
        }, id);
    }

    private static CourseView mapCourse(ResultSet row) throws SQLException {
        return new CourseView(Long.toString(row.getLong("id")), row.getString("title"),
                row.getString("description"), row.getString("status"), row.getInt("pass_score"),
                row.getInt("estimated_minutes"), row.getLong("version"));
    }

    private static ChapterView mapChapter(ResultSet row) throws SQLException {
        return new ChapterView(Long.toString(row.getLong("id")), Long.toString(row.getLong("course_id")),
                row.getString("title"), row.getInt("sequence_no"), row.getInt("minimum_active_seconds"),
                row.getLong("version"));
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private List<String> read(String value) {
        try {
            return json.readValue(value, new TypeReference<>() {});
        } catch (Exception exception) {
            return List.of();
        }
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.strip().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static long next() {
        return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    }

    private static long id(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
}
