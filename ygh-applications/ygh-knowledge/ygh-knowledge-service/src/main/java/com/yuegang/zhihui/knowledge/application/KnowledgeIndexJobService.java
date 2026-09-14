package com.yuegang.zhihui.knowledge.application;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.knowledge.api.KnowledgeIndexJobView;
import com.yuegang.zhihui.knowledge.api.RebuildKnowledgeIndexResponse;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

public final class KnowledgeIndexJobService {
    private final JdbcTemplate jdbc;

    public KnowledgeIndexJobService(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public List<KnowledgeIndexJobView> list(String documentId, String status, int limit) {
        StringBuilder sql = new StringBuilder("SELECT id,document_id,index_version,job_type,status,retry_count,last_error,updated_at FROM knowledge_index_job WHERE 1=1");
        var arguments = new java.util.ArrayList<>();
        if (documentId != null && !documentId.isBlank()) {
            sql.append(" AND document_id=?");
            arguments.add(positive(documentId));
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status=?");
            arguments.add(status.strip().toUpperCase(java.util.Locale.ROOT));
        }
        sql.append(" ORDER BY updated_at DESC LIMIT ?");
        arguments.add(Math.max(1, Math.min(limit, 100)));
        return jdbc.query(sql.toString(), (row, number) -> view(
                row.getLong(1), row.getLong(2), row.getString(3), row.getString(4),
                row.getString(5), row.getInt(6), row.getString(7), row.getTimestamp(8)),
                arguments.toArray());
    }

    public KnowledgeIndexJobView retry(String id) {
        long jobId = positive(id);
        int updated = jdbc.update("UPDATE knowledge_index_job SET status='RETRY',retry_count=0,next_retry_at=NOW(6),last_error=NULL WHERE id=? AND status='FAILED'", jobId);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT);
        }
        return jdbc.query("SELECT id,document_id,index_version,job_type,status,retry_count,last_error,updated_at FROM knowledge_index_job WHERE id=?",
                result -> {
                    if (!result.next()) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
                    return view(result.getLong(1), result.getLong(2), result.getString(3), result.getString(4),
                            result.getString(5), result.getInt(6), result.getString(7), result.getTimestamp(8));
                }, jobId);
    }

    /** Queues an idempotent full rebuild from the authoritative published-document set. */
    public RebuildKnowledgeIndexResponse rebuild(String version) {
        if (version == null || !version.matches("[a-z0-9][a-z0-9._-]{1,63}")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        List<Long> documents = jdbc.queryForList("""
                SELECT d.id FROM knowledge_document d
                WHERE d.status='PUBLISHED'
                  AND (d.expires_at IS NULL OR d.expires_at>NOW(6))
                  AND NOT EXISTS (
                    SELECT 1 FROM knowledge_index_job j
                    WHERE j.document_id=d.id AND j.index_version=? AND j.job_type='UPSERT'
                  )
                ORDER BY d.id
                """, Long.class, version);
        for (Long document : documents) {
            jdbc.update("INSERT INTO knowledge_index_job(id,document_id,index_version,job_type,status) VALUES(?,?,?,'UPSERT','PENDING')",
                    nextId(), document, version);
        }
        return new RebuildKnowledgeIndexResponse(version, documents.size());
    }

    private static KnowledgeIndexJobView view(long id, long documentId, String version, String type,
                                               String status, int retries, String failure, Timestamp updatedAt) {
        int progress = switch (status) {
            case "SUCCEEDED" -> 100;
            case "PROCESSING" -> 75;
            case "PENDING", "RETRY" -> 50;
            default -> 0;
        };
        OffsetDateTime time = updatedAt.toInstant().atOffset(ZoneOffset.UTC);
        return new KnowledgeIndexJobView(Long.toString(id), Long.toString(documentId), version, type,
                status, progress, retries, failure, time);
    }

    private static long positive(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) throw new NumberFormatException();
            return parsed;
        } catch (RuntimeException failure) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private static long nextId() {
        return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    }
}
