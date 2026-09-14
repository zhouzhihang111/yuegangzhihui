package com.yuegang.zhihui.knowledge.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.knowledge.api.KnowledgeMetadataView;
import com.yuegang.zhihui.knowledge.api.UpdateKnowledgeMetadataRequest;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

public class KnowledgeMetadataService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public KnowledgeMetadataService(DataSource dataSource, ObjectMapper json) {
        jdbc = new JdbcTemplate(dataSource);
        this.json = json;
    }

    @Transactional
    public KnowledgeMetadataView update(long operator, String document, UpdateKnowledgeMetadataRequest command) {
        long id = id(document);
        if (command.effectiveFrom() != null && command.expiresAt() != null
                && command.expiresAt().toLocalDate().isBefore(command.effectiveFrom())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        int updated = jdbc.update("""
                UPDATE knowledge_document
                SET issuing_authority=?,effective_from=?,expires_at=?,region=?,classification=?,visibility=?,
                    source_name=?,metadata_version=metadata_version+1
                WHERE id=? AND metadata_version=? AND status IN ('DRAFT','PENDING_REVIEW','REJECTED')
                """, command.issuingAuthority(), command.effectiveFrom(),
                command.expiresAt() == null ? null : Timestamp.from(command.expiresAt().toInstant()),
                command.region(), command.classification(), command.classification(), command.sourceName(), id,
                command.version());
        if (updated != 1) throw new BusinessException(ErrorCode.BUSINESS_CONFLICT);
        jdbc.update("DELETE FROM knowledge_document_tag WHERE document_id=?", id);
        for (String name : command.tags()) {
            String tag = name.strip();
            if (tag.isBlank()) continue;
            long tagId = Math.abs((long) tag.hashCode()) + 1;
            jdbc.update("INSERT INTO knowledge_tag(id,name) VALUES(?,?) ON DUPLICATE KEY UPDATE id=id", tagId, tag);
            Long existing = jdbc.queryForObject("SELECT id FROM knowledge_tag WHERE name=?", Long.class, tag);
            jdbc.update("INSERT INTO knowledge_document_tag(document_id,tag_id) VALUES(?,?)", id, existing);
        }
        KnowledgeMetadataView row = view(id);
        Map<String, Object> snapshot = Map.of(
                "issuingAuthority", Objects.toString(command.issuingAuthority(), ""),
                "effectiveFrom", Objects.toString(command.effectiveFrom(), ""),
                "expiresAt", Objects.toString(command.expiresAt(), ""),
                "region", Objects.toString(command.region(), ""),
                "classification", command.classification(),
                "sourceName", Objects.toString(command.sourceName(), ""),
                "tags", command.tags());
        String title = jdbc.queryForObject("SELECT title FROM knowledge_document WHERE id=?", String.class, id);
        String sha = jdbc.queryForObject("SELECT sha256 FROM knowledge_document WHERE id=?", String.class, id);
        jdbc.update("INSERT INTO knowledge_document_version(id,document_id,version_no,title,metadata_json,content_sha256,created_by) VALUES(?,?,?,?,?,?,?)",
                next(), id, row.metadataVersion(), title, write(snapshot), sha, operator);
        return row;
    }

    public KnowledgeMetadataView view(long id) {
        return jdbc.query("""
                SELECT id,issuing_authority,effective_from,expires_at,region,classification,source_name,metadata_version
                FROM knowledge_document WHERE id=?
                """, result -> {
            if (!result.next()) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            Date effective = result.getDate(3);
            Timestamp expires = result.getTimestamp(4);
            var tags = jdbc.queryForList("SELECT t.name FROM knowledge_tag t JOIN knowledge_document_tag dt ON dt.tag_id=t.id WHERE dt.document_id=? ORDER BY t.name",
                    String.class, id);
            return new KnowledgeMetadataView(Long.toString(result.getLong(1)), result.getString(2),
                    effective == null ? null : effective.toLocalDate(),
                    expires == null ? null : expires.toInstant().atOffset(ZoneOffset.UTC), result.getString(5),
                    result.getString(6), result.getString(7), tags, result.getLong(8));
        }, id);
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    private static long id(String value) {
        try { return Long.parseLong(value); }
        catch (Exception exception) { throw new BusinessException(ErrorCode.VALIDATION_ERROR); }
    }
    private static long next() { return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE; }
}
