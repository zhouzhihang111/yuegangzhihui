package com.yuegang.zhihui.common.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuegang.zhihui.common.core.PageRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
        classes = MybatisRuntimeIntegrationTest.TestApplication.class,
        properties = {
            "spring.datasource.url=jdbc:h2:mem:ygh_audit;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.sql.init.mode=always",
            "spring.flyway.enabled=false"
        })
class MybatisRuntimeIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-11T11:00:00Z");

    @Autowired
    private AuditTestMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableAuditorProvider auditorProvider;

    @Test
    void realMapperProtectsCreationAuditAndRefreshesModificationAudit() {
        var entity = entity(1L, "first");
        entity.setCreatedBy("forged-creator");
        entity.setUpdatedBy("forged-editor");

        mapper.insert(entity);

        assertThat(auditValue(1L, "created_by")).isEqualTo("user-1");
        assertThat(auditValue(1L, "updated_by")).isEqualTo("user-1");

        auditorProvider.use("editor-2");
        entity.setName("changed");
        entity.setCreatedBy("forged-update");
        mapper.updateById(entity);

        assertThat(auditValue(1L, "created_by")).isEqualTo("user-1");
        assertThat(auditValue(1L, "updated_by")).isEqualTo("editor-2");
    }

    @Test
    void realMapperUsesBoundedOneBasedPagination() {
        mapper.insert(entity(11L, "A"));
        mapper.insert(entity(12L, "B"));
        mapper.insert(entity(13L, "C"));

        var page = mapper.selectPage(
                MybatisPageAdapter.toMybatisPage(new PageRequest(1, 2)),
                null);

        assertThat(page.getCurrent()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(2);
        assertThat(page.getTotal()).isEqualTo(3);
        assertThat(page.getRecords()).hasSize(2);
    }

    private String auditValue(long id, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM audit_test WHERE id = ?",
                String.class,
                id);
    }

    private static AuditTestEntity entity(long id, String name) {
        var entity = new AuditTestEntity();
        entity.setId(id);
        entity.setName(name);
        return entity;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(
            basePackageClasses = AuditTestMapper.class,
            annotationClass = Mapper.class)
    static class TestApplication {

        @Bean
        MutableAuditorProvider auditorProvider() {
            return new MutableAuditorProvider("user-1");
        }

        @Bean
        Clock auditClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}

final class MutableAuditorProvider implements AuditorProvider {

    private final AtomicReference<String> auditor;

    MutableAuditorProvider(String auditor) {
        this.auditor = new AtomicReference<>(auditor);
    }

    void use(String value) {
        auditor.set(value);
    }

    @Override
    public String currentAuditor() {
        return auditor.get();
    }
}

@Mapper
interface AuditTestMapper extends BaseMapper<AuditTestEntity> {
}

@TableName("audit_test")
class AuditTestEntity extends AuditableEntity {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String name;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
