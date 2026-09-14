package com.yuegang.zhihui.knowledge.api;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.knowledge.application.KnowledgeAccessGuard;
import com.yuegang.zhihui.knowledge.security.KnowledgeUserContext;
import com.yuegang.zhihui.knowledge.security.KnowledgeUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
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
@RequestMapping("/api/v1/knowledge/documents")
public final class KnowledgeDownloadController {
    private final JdbcTemplate jdbc;
    private final KnowledgeUserResolver users;
    private final KnowledgeAccessGuard access;
    private final Path root;

    public KnowledgeDownloadController(DataSource dataSource, KnowledgeUserResolver users,
                                       KnowledgeAccessGuard access,
                                       @Value("${ygh.knowledge.storage-root}") String root) {
        jdbc = new JdbcTemplate(dataSource);
        this.users = users;
        this.access = access;
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @GetMapping("/{id}/content")
    ResponseEntity<Resource> content(@PathVariable String id,
                                     @RequestParam(defaultValue = "true") boolean inline,
                                     HttpServletRequest request) {
        long documentId = positive(id);
        KnowledgeUserContext user = users.resolveContext(request);
        if (!user.administrator()) access.requirePublished(documentId, user.knowledgeVisibilities());
        Row row = jdbc.query("SELECT file_name,media_type,storage_key,size_bytes FROM knowledge_document WHERE id=?",
                result -> {
                    if (!result.next()) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
                    return new Row(result.getString(1), result.getString(2), result.getString(3), result.getLong(4));
                }, documentId);
        Path file = root.resolve(row.key()).normalize();
        if (file.startsWith(root) && Files.isRegularFile(file)) {
            Resource resource = new FileSystemResource(file);
            ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                    .filename(row.name(), StandardCharsets.UTF_8).build();
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(row.media())).contentLength(row.size())
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .header("X-Content-Type-Options", "nosniff").body(resource);
        }

        byte[] fallback = parsedChunks(documentId, row.name()).getBytes(StandardCharsets.UTF_8);
        Resource resource = new ByteArrayResource(fallback);
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(row.name() + ".txt", StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).contentLength(fallback.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("X-YGH-Content-Fallback", "parsed-chunks").body(resource);
    }

    private String parsedChunks(long documentId, String fileName) {
        List<String> chunks = jdbc.queryForList("""
                SELECT content FROM knowledge_chunk
                WHERE document_id=? AND status='ACTIVE'
                ORDER BY chunk_index
                """, String.class, documentId);
        if (chunks.isEmpty()) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return "原始附件未同步，以下为知识库已解析正文，仅供页面预览和检索核对。\n\n文件：" + fileName
                + "\n\n" + String.join("\n\n", chunks);
    }

    private record Row(String name, String media, String key, long size) { }

    private static long positive(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
}
