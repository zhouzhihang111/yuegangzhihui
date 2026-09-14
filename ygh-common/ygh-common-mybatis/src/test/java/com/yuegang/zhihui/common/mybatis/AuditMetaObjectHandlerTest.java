package com.yuegang.zhihui.common.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.Test;

class AuditMetaObjectHandlerTest {

    private static final Instant FIRST = Instant.parse("2026-07-11T11:00:00Z");
    private static final Instant SECOND = Instant.parse("2026-07-11T12:00:00Z");

    @Test
    void insertFillsAllAuditFieldsWithOneActorAndOneInstant() {
        var entity = new TestEntity();
        handler("user-1", FIRST).insertFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getCreatedBy()).isEqualTo("user-1");
        assertThat(entity.getUpdatedBy()).isEqualTo("user-1");
        assertThat(entity.getCreatedAt()).isEqualTo(FIRST);
        assertThat(entity.getUpdatedAt()).isEqualTo(FIRST);
    }

    @Test
    void insertOverwritesCallerSuppliedAuditValues() {
        var entity = auditedAt("import-user", FIRST);

        handler("user-1", SECOND).insertFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getCreatedBy()).isEqualTo("user-1");
        assertThat(entity.getUpdatedBy()).isEqualTo("user-1");
        assertThat(entity.getCreatedAt()).isEqualTo(SECOND);
        assertThat(entity.getUpdatedAt()).isEqualTo(SECOND);
    }

    @Test
    void updateOverwritesModificationAuditWithoutChangingCreationAudit() {
        var entity = auditedAt("creator", FIRST);

        handler("editor", SECOND).updateFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getCreatedBy()).isEqualTo("creator");
        assertThat(entity.getCreatedAt()).isEqualTo(FIRST);
        assertThat(entity.getUpdatedBy()).isEqualTo("editor");
        assertThat(entity.getUpdatedAt()).isEqualTo(SECOND);
    }

    @Test
    void blankAuditorFailsFastInsteadOfWritingUntraceableRows() {
        var entity = new TestEntity();

        assertThatThrownBy(() -> handler(" ", FIRST)
                        .insertFill(SystemMetaObject.forObject(entity)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auditor");
    }

    @Test
    void handlerSafelyIgnoresObjectsWithoutAuditProperties() {
        var plain = new Object();
        var handler = handler("user-1", FIRST);

        handler.insertFill(SystemMetaObject.forObject(plain));
        handler.updateFill(SystemMetaObject.forObject(plain));
    }

    private static AuditMetaObjectHandler handler(String auditor, Instant instant) {
        return new AuditMetaObjectHandler(
                () -> auditor,
                Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static TestEntity auditedAt(String auditor, Instant instant) {
        var entity = new TestEntity();
        entity.setCreatedBy(auditor);
        entity.setCreatedAt(instant);
        entity.setUpdatedBy(auditor);
        entity.setUpdatedAt(instant);
        return entity;
    }

    private static final class TestEntity extends AuditableEntity {
    }
}
