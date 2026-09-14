package com.yuegang.zhihui.knowledge.application;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

public final class KnowledgeAccessGuard {
    private final JdbcTemplate jdbc;

    public KnowledgeAccessGuard(DataSource dataSource) {
        jdbc = new JdbcTemplate(dataSource);
    }

    public void requirePublished(long documentId, Set<String> visibilities) {
        List<String> allowed = visibilities.stream().sorted().toList();
        if (allowed.isEmpty()) throw notFound();
        String markers = String.join(",", Collections.nCopies(allowed.size(), "?"));
        List<Object> arguments = new ArrayList<>();
        arguments.add(documentId);
        arguments.addAll(allowed);
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM knowledge_document
                WHERE id=? AND status='PUBLISHED' AND (expires_at IS NULL OR expires_at>NOW(6))
                  AND visibility IN (%s)
                """.formatted(markers), Integer.class, arguments.toArray());
        if (count == null || count != 1) throw notFound();
    }

    private static BusinessException notFound() { return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND); }
}
