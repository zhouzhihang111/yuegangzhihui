package com.yuegang.zhihui.common.test;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/** Resource-bounded Testcontainer fixtures aligned with constrained-dev image versions. */
public final class YghTestContainerFactory {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long MIB = 1024L * 1024L;

    private YghTestContainerFactory() {
    }

    public static GenericContainer<?> redis() {
        return bounded(new GenericContainer<>(DockerImageName.parse("redis:8.4.4"))
                .withExposedPorts(6379)
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofSeconds(60)), 128L);
    }

    public static JdbcContainerFixture mysql() {
        String credential = randomCredential();
        String rootCredential = randomCredential();
        var container = bounded(new GenericContainer<>(DockerImageName.parse("mysql:8.4.10"))
                .withEnv("MYSQL_DATABASE", "ygh_test")
                .withEnv("MYSQL_USER", "ygh_test")
                .withEnv("MYSQL_PASSWORD", credential)
                .withEnv("MYSQL_ROOT_PASSWORD", rootCredential)
                .withExposedPorts(3306)
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofMinutes(2)), 768L);
        return new JdbcContainerFixture(
                container, "ygh_test", "ygh_test", credential,
                "root", rootCredential, 3306, "mysql");
    }

    public static JdbcContainerFixture pgvector() {
        String credential = randomCredential();
        var container = bounded(new GenericContainer<>(DockerImageName.parse(
                        "pgvector/pgvector:0.8.5-pg17-bookworm"))
                .withEnv("POSTGRES_DB", "ygh_test")
                .withEnv("POSTGRES_USER", "ygh_test")
                .withEnv("POSTGRES_PASSWORD", credential)
                .withExposedPorts(5432)
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofMinutes(2)), 512L);
        return new JdbcContainerFixture(
                container, "ygh_test", "ygh_test", credential,
                "ygh_test", credential, 5432, "postgresql");
    }

    private static GenericContainer<?> bounded(
            GenericContainer<?> container,
            long memoryMiB
    ) {
        container.withCreateContainerCmdModifier(command ->
                command.getHostConfig().withMemory(memoryMiB * MIB));
        return container;
    }

    private static String randomCredential() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
