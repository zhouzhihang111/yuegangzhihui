package com.yuegang.zhihui.knowledge.application;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.knowledge.api.KnowledgeDocumentView;
import com.yuegang.zhihui.knowledge.api.KnowledgeStatus;
import com.yuegang.zhihui.knowledge.api.ReviewKnowledgeRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.DigestOutputStream;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.tika.Tika;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

public final class KnowledgeDocumentService {
    private static final Set<String> ALLOWED = Set.of("application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain", "text/markdown");
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Path root;
    private final Tika tika = new Tika();
    private final KnowledgeParseDispatcher parser;

    public KnowledgeDocumentService(DataSource dataSource, String storage) {
        this(dataSource, storage, new KnowledgeParseDispatcher(dataSource, storage));
    }

    public KnowledgeDocumentService(DataSource dataSource, String storage, KnowledgeParseDispatcher parser) {
        jdbc = new JdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        root = Path.of(storage).toAbsolutePath().normalize();
        this.parser = parser;
        try { Files.createDirectories(root); }
        catch (IOException failure) { throw new IllegalStateException(failure); }
    }

    public KnowledgeDocumentView upload(long user, String title, String category, MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 50L * 1024 * 1024) throw invalid();
        String original = Optional.ofNullable(file.getOriginalFilename()).orElse("document");
        if (original.contains("/") || original.contains("\\") || original.indexOf('\0') >= 0) throw invalid();
        long document = next();
        long job = next();
        Path target = root.resolve(document + ".bin").normalize();
        if (!target.startsWith(root)) throw invalid();
        try {
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            String detected = detect(target, original);
            if (!ALLOWED.contains(detected)) {
                cleanup(target);
                throw invalid();
            }
            String sha = digest(target);
            transactions.executeWithoutResult(status -> {
                jdbc.update("INSERT INTO knowledge_document(id,title,category,file_name,media_type,size_bytes,sha256,storage_key,status,uploaded_by) VALUES(?,?,?,?,?,?,?,?, 'PROCESSING',?)",
                        document, title.trim(), category, original, detected, file.getSize(), sha,
                        target.getFileName().toString(), user);
                history(document, null, "UPLOADED", user, null);
                history(document, "UPLOADED", "SECURITY_CHECKED", user, null);
                history(document, "SECURITY_CHECKED", "PROCESSING", user, null);
                jdbc.update("INSERT INTO knowledge_processing_job(id,document_id,task_type,status,progress) VALUES(?,?,'PARSE','PENDING',0)",
                        job, document);
            });
            parser.process(job);
            return get(document);
        } catch (IOException failure) {
            cleanup(target);
            throw new IllegalStateException("document IO failed", failure);
        } catch (RuntimeException failure) {
            if (!exists(document)) cleanup(target);
            throw failure;
        }
    }

    public KnowledgeDocumentView review(long reviewer, String document, ReviewKnowledgeRequest command) {
        return transactions.execute(status -> {
            long id = id(document);
            String target = command.decision() == ReviewKnowledgeRequest.Decision.APPROVE
                    ? "PUBLISHED" : "REJECTED";
            int changed = jdbc.update("UPDATE knowledge_document SET status=?,reviewed_by=?,review_comment=?,published_at=CASE WHEN ?='PUBLISHED' THEN NOW(6) ELSE NULL END,version=version+1 WHERE id=? AND status='PENDING_REVIEW' AND version=?",
                    target, reviewer, command.comment(), target, id, command.version());
            if (changed != 1) throw new BusinessException(ErrorCode.BUSINESS_CONFLICT);
            jdbc.update("INSERT INTO knowledge_review(id,document_id,reviewer_id,decision,comment) VALUES(?,?,?,?,?)",
                    next(), id, reviewer, command.decision().name(), command.comment());
            history(id, "PENDING_REVIEW", target, reviewer, command.comment());
            if ("PUBLISHED".equals(target)) {
                jdbc.update("INSERT INTO knowledge_index_job(id,document_id,index_version,job_type,status) VALUES(?,?,?,'UPSERT','PENDING')",
                        next(), id, "knowledge-active");
            }
            var data = jdbc.queryForMap("SELECT uploaded_by,title FROM knowledge_document WHERE id=?", id);
            String event = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO knowledge_outbox(id,aggregate_id,event_type,payload_json) VALUES(?,?, 'KNOWLEDGE_REVIEWED',JSON_OBJECT('userId',?,'documentId',?,'title',?,'decision',?))",
                    event, Long.toString(id), data.get("uploaded_by"), Long.toString(id), data.get("title"), target);
            return get(id);
        });
    }

    public KnowledgeDocumentView get(long id) {
        return jdbc.query("SELECT id,title,category,file_name,media_type,size_bytes,sha256,status,version,updated_at FROM knowledge_document WHERE id=?",
                result -> {
                    if (!result.next()) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
                    return new KnowledgeDocumentView(Long.toString(result.getLong(1)), result.getString(2),
                            result.getString(3), result.getString(4), result.getString(5), result.getLong(6),
                            result.getString(7), KnowledgeStatus.valueOf(result.getString(8)), result.getLong(9),
                            result.getTimestamp(10).toLocalDateTime().atOffset(ZoneOffset.UTC));
                }, id);
    }

    private boolean exists(long id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_document WHERE id=?", Integer.class, id);
        return count != null && count > 0;
    }

    private String detect(Path path, String name) throws IOException {
        try (InputStream input = Files.newInputStream(path)) { return tika.detect(input, name); }
    }

    private void history(long document, String from, String to, long user, String reason) {
        jdbc.update("INSERT INTO knowledge_status_history(id,document_id,from_status,to_status,operator_id,reason) VALUES(?,?,?,?,?,?)",
                next(), document, from, to, user, reason);
    }

    private static void cleanup(Path path) {
        try { Files.deleteIfExists(path); }
        catch (IOException ignored) { }
    }

    private static String digest(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            input.transferTo(new DigestOutputStream(OutputStream.nullOutputStream(), digest));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static long next() { return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE; }

    private static long id(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException failure) {
            throw invalid();
        }
    }

    private static BusinessException invalid() { return new BusinessException(ErrorCode.VALIDATION_ERROR); }
}
