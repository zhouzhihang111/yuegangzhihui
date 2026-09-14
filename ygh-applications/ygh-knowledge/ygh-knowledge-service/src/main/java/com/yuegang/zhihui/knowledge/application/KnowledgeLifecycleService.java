package com.yuegang.zhihui.knowledge.application;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.knowledge.api.KnowledgeDocumentView;
import com.yuegang.zhihui.knowledge.api.KnowledgeStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class KnowledgeLifecycleService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public KnowledgeLifecycleService(DataSource dataSource) {
        jdbc = new JdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    public List<KnowledgeDocumentView> list(String status, String category, boolean publicOnly, int limit) {
        return list(status, category, publicOnly, limit, Set.of("PUBLIC", "INTERNAL", "CONFIDENTIAL"));
    }

    public List<KnowledgeDocumentView> list(String status, String category, boolean publicOnly, int limit,
                                            Set<String> visibilities) {
        StringBuilder query = new StringBuilder(
                "SELECT id,title,category,file_name,media_type,size_bytes,sha256,status,version,updated_at FROM knowledge_document WHERE 1=1");
        List<Object> arguments = new ArrayList<>();
        if (publicOnly) {
            if (visibilities.isEmpty()) return List.of();
            query.append(" AND status='PUBLISHED' AND (expires_at IS NULL OR expires_at>NOW(6)) AND visibility IN (")
                    .append(String.join(",", Collections.nCopies(visibilities.size(), "?"))).append(')');
            arguments.addAll(visibilities.stream().sorted().toList());
        } else if (status != null && !status.isBlank()) {
            query.append(" AND status=?");
            arguments.add(status);
        }
        if (category != null && !category.isBlank()) {
            query.append(" AND category=?");
            arguments.add(category);
        }
        query.append(" ORDER BY updated_at DESC LIMIT ?");
        arguments.add(Math.max(1, Math.min(limit, 100)));
        return jdbc.query(query.toString(), (result, row) -> map(result), arguments.toArray());
    }

    public KnowledgeDocumentView offline(long operator, String document, long version, String reason) {
        return transactions.execute(status -> {
            long id = id(document);
            if (jdbc.update("UPDATE knowledge_document SET status='OFFLINE',version=version+1 WHERE id=? AND version=? AND status='PUBLISHED'",
                    id, version) != 1) throw new BusinessException(ErrorCode.BUSINESS_CONFLICT);
            jdbc.update("INSERT INTO knowledge_status_history(id,document_id,from_status,to_status,operator_id,reason) VALUES(?,?, 'PUBLISHED','OFFLINE',?,?)",
                    next(), id, operator, reason);
            jdbc.update("INSERT INTO knowledge_index_job(id,document_id,index_version,job_type,status) VALUES(?,?,?,'DELETE','PENDING')",
                    next(), id, "knowledge-v" + System.currentTimeMillis());
            return get(id);
        });
    }

    private KnowledgeDocumentView get(long id) {
        return jdbc.query("SELECT id,title,category,file_name,media_type,size_bytes,sha256,status,version,updated_at FROM knowledge_document WHERE id=?",
                result -> {
                    if (!result.next()) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
                    return map(result);
                }, id);
    }

    private static KnowledgeDocumentView map(ResultSet result) throws SQLException {
        return new KnowledgeDocumentView(Long.toString(result.getLong(1)), result.getString(2), result.getString(3),
                result.getString(4), result.getString(5), result.getLong(6), result.getString(7),
                KnowledgeStatus.valueOf(result.getString(8)), result.getLong(9),
                result.getTimestamp(10).toLocalDateTime().atOffset(ZoneOffset.UTC));
    }
    private static long next() { return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE; }
    private static long id(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) throw new NumberFormatException();
            return parsed;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
}
