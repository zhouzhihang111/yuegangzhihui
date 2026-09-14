package com.yuegang.zhihui.common.test;

import java.util.Objects;
import org.testcontainers.containers.GenericContainer;

/** Runtime-generated JDBC Testcontainer credentials with redacted diagnostic output. */
public final class JdbcContainerFixture implements AutoCloseable {

    private final GenericContainer<?> container;
    private final String database;
    private final String username;
    private final String credential;
    private final String adminUsername;
    private final String adminCredential;
    private final int port;
    private final String jdbcScheme;

    JdbcContainerFixture(
            GenericContainer<?> container,
            String database,
            String username,
            String credential,
            String adminUsername,
            String adminCredential,
            int port,
            String jdbcScheme
    ) {
        this.container = Objects.requireNonNull(container, "container must not be null");
        this.database = Objects.requireNonNull(database, "database must not be null");
        this.username = Objects.requireNonNull(username, "username must not be null");
        this.credential = Objects.requireNonNull(credential, "credential must not be null");
        this.adminUsername = Objects.requireNonNull(adminUsername, "adminUsername must not be null");
        this.adminCredential = Objects.requireNonNull(adminCredential, "adminCredential must not be null");
        this.port = port;
        this.jdbcScheme = Objects.requireNonNull(jdbcScheme, "jdbcScheme must not be null");
    }

    public GenericContainer<?> container() {
        return container;
    }

    public String database() {
        return database;
    }

    public String username() {
        return username;
    }

    public String credential() {
        return credential;
    }

    public String adminUsername() {
        return adminUsername;
    }

    public String adminCredential() {
        return adminCredential;
    }

    public JdbcContainerFixture start() {
        container.start();
        return this;
    }

    public String jdbcUrl() {
        if (!container.isRunning()) {
            throw new IllegalStateException("container must be started before requesting JDBC URL");
        }
        return "jdbc:" + jdbcScheme + "://" + container.getHost() + ':'
                + container.getMappedPort(port) + '/' + database;
    }

    @Override
    public void close() {
        container.stop();
    }

    @Override
    public String toString() {
        return "JdbcContainerFixture[image=" + container.getDockerImageName()
                + ", database=" + database + ", username=" + username
                + ", credential=[REDACTED]]";
    }
}
