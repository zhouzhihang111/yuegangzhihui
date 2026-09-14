package com.yuegang.zhihui.common.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class YghTestContainerFactoryTest {

    @Test
    void usesDeploymentAlignedPinnedImagesWithoutStartingDocker() {
        var redis = YghTestContainerFactory.redis();
        var mysql = YghTestContainerFactory.mysql();
        var pgvector = YghTestContainerFactory.pgvector();

        assertThat(redis.getDockerImageName()).isEqualTo("redis:8.4.4");
        assertThat(mysql.container().getDockerImageName()).isEqualTo("mysql:8.4.10");
        assertThat(pgvector.container().getDockerImageName())
                .isEqualTo("pgvector/pgvector:0.8.5-pg17-bookworm");
        assertThat(mysql.credential()).hasSizeGreaterThanOrEqualTo(32);
        assertThat(mysql.adminCredential()).hasSizeGreaterThanOrEqualTo(32)
                .isNotEqualTo(mysql.credential());
        assertThat(pgvector.credential()).hasSizeGreaterThanOrEqualTo(32);
        assertThat(mysql.toString()).contains("[REDACTED]")
                .doesNotContain(mysql.credential(), mysql.adminCredential());
        assertThat(mysql).isInstanceOf(AutoCloseable.class);
        assertThatThrownBy(mysql::jdbcUrl).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void everyDatabaseFixtureGetsDifferentRuntimeCredentials() {
        assertThat(YghTestContainerFactory.mysql().credential())
                .isNotEqualTo(YghTestContainerFactory.mysql().credential());
    }
}
