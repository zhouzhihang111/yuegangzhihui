package com.yuegang.zhihui.common.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuditableEntityContractTest {

    @Test
    void newEntityStartsWithoutForgedAuditData() {
        var entity = new TestEntity();

        assertThat(entity.getCreatedBy()).isNull();
        assertThat(entity.getCreatedAt()).isNull();
        assertThat(entity.getUpdatedBy()).isNull();
        assertThat(entity.getUpdatedAt()).isNull();
    }

    @Test
    void fieldsExposeTheExpectedMybatisFillPolicies() throws NoSuchFieldException {
        assertFill("createdBy", FieldFill.INSERT);
        assertFill("createdAt", FieldFill.INSERT);
        assertFill("updatedBy", FieldFill.INSERT_UPDATE);
        assertFill("updatedAt", FieldFill.INSERT_UPDATE);
        assertThat(AuditableEntity.class.getDeclaredField("createdBy")
                .getAnnotation(TableField.class).updateStrategy()).isEqualTo(FieldStrategy.NEVER);
        assertThat(AuditableEntity.class.getDeclaredField("createdAt")
                .getAnnotation(TableField.class).updateStrategy()).isEqualTo(FieldStrategy.NEVER);
    }

    @Test
    void inheritedAuditPropertiesRemainWritableToMybatis() {
        var entity = new TestEntity();
        var now = Instant.parse("2026-07-11T11:00:00Z");

        entity.setCreatedBy("user-9007199254740993");
        entity.setCreatedAt(now);
        entity.setUpdatedBy("SYSTEM");
        entity.setUpdatedAt(now);

        assertThat(entity.getCreatedBy()).isEqualTo("user-9007199254740993");
        assertThat(entity.getCreatedAt()).isEqualTo(now);
        assertThat(entity.getUpdatedBy()).isEqualTo("SYSTEM");
        assertThat(entity.getUpdatedAt()).isEqualTo(now);
    }

    private static void assertFill(String fieldName, FieldFill expected) throws NoSuchFieldException {
        var field = AuditableEntity.class.getDeclaredField(fieldName);
        assertThat(field.getAnnotation(TableField.class).fill()).isEqualTo(expected);
    }

    private static final class TestEntity extends AuditableEntity {
    }
}
