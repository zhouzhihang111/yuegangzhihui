package com.yuegang.zhihui.common.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.apache.ibatis.reflection.MetaObject;

/** Fills immutable creation audit data and refreshes modification audit data. */
public final class AuditMetaObjectHandler implements MetaObjectHandler {

    private static final String CREATED_BY = "createdBy";
    private static final String CREATED_AT = "createdAt";
    private static final String UPDATED_BY = "updatedBy";
    private static final String UPDATED_AT = "updatedAt";

    private final AuditorProvider auditorProvider;
    private final Clock clock;

    public AuditMetaObjectHandler(AuditorProvider auditorProvider, Clock clock) {
        this.auditorProvider = Objects.requireNonNull(
                auditorProvider, "auditorProvider must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        Objects.requireNonNull(metaObject, "metaObject must not be null");
        if (!hasAnyAuditProperty(metaObject)) {
            return;
        }

        String auditor = resolveAuditor();
        Instant now = clock.instant();
        setIfPresent(metaObject, CREATED_BY, auditor);
        setIfPresent(metaObject, CREATED_AT, now);
        setIfPresent(metaObject, UPDATED_BY, auditor);
        setIfPresent(metaObject, UPDATED_AT, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        Objects.requireNonNull(metaObject, "metaObject must not be null");
        if (!hasAnyAuditProperty(metaObject)) {
            return;
        }

        String auditor = resolveAuditor();
        Instant now = clock.instant();
        setIfPresent(metaObject, UPDATED_BY, auditor);
        setIfPresent(metaObject, UPDATED_AT, now);
    }

    private String resolveAuditor() {
        String auditor = auditorProvider.currentAuditor();
        if (auditor == null || auditor.isBlank()) {
            throw new IllegalStateException("auditor must not be blank");
        }
        return auditor;
    }

    private static boolean hasAnyAuditProperty(MetaObject metaObject) {
        return metaObject.hasSetter(CREATED_BY)
                || metaObject.hasSetter(CREATED_AT)
                || metaObject.hasSetter(UPDATED_BY)
                || metaObject.hasSetter(UPDATED_AT);
    }

    private static void setIfPresent(MetaObject metaObject, String property, Object value) {
        if (metaObject.hasSetter(property)) {
            metaObject.setValue(property, value);
        }
    }
}
