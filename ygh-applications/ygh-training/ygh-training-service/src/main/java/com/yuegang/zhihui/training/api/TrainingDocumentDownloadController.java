package com.yuegang.zhihui.training.api;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.training.application.TrainingDocumentProgressService;
import com.yuegang.zhihui.training.security.TrainingUserContext;
import com.yuegang.zhihui.training.security.TrainingUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/training/documents")
public final class TrainingDocumentDownloadController {
    private final JdbcTemplate jdbc;
    private final TrainingUserResolver users;
    private final TrainingDocumentProgressService progress;
    private final Path root;

    public TrainingDocumentDownloadController(DataSource dataSource, TrainingUserResolver users,
                                              TrainingDocumentProgressService progress,
                                              @Value("${ygh.training.storage-root}") String storageRoot) {
        jdbc = new JdbcTemplate(dataSource);
        this.users = users;
        this.progress = progress;
        root = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    @GetMapping("/{id}/content")
    ResponseEntity<Resource> content(@PathVariable String id,
                                     @RequestParam(defaultValue = "true") boolean inline,
                                     @RequestParam(required = false) String assignmentId,
                                     HttpServletRequest request) {
        TrainingUserContext context = users.context(request);
        long user = context.userId(), document = positive(id);
        Row row = jdbc.query("""
                SELECT d.file_name,d.media_type,d.storage_key,d.size_bytes,c.course_id
                FROM training_document d JOIN training_chapter c ON c.id=d.chapter_id
                WHERE d.id=? AND d.status='ACTIVE'
                """, result -> {
            if (!result.next()) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            return new Row(result.getString(1), result.getString(2), result.getString(3),
                    result.getLong(4), result.getLong(5));
        }, document);
        if (!context.courseManager()) {
            if (assignmentId == null || assignmentId.isBlank()) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            progress.recordOpened(user, assignmentId, id);
        }
        Path file = root.resolve(row.key()).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        Resource resource = new FileSystemResource(file);
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(row.name(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(row.media())).contentLength(row.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff").body(resource);
    }

    private record Row(String name, String media, String key, long size, long course) {}
    private static long positive(String value) {
        try { long parsed = Long.parseLong(value); if (parsed <= 0) throw new NumberFormatException(); return parsed; }
        catch (RuntimeException invalid) { throw new BusinessException(ErrorCode.VALIDATION_ERROR); }
    }
}
