package com.yuegang.zhihui.common.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuegang.zhihui.common.core.EventMetadata;
import com.yuegang.zhihui.common.core.ImmutableEventPayload;
import com.yuegang.zhihui.common.core.VersionedDomainEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.DefaultTransactionStatus;

class JdbcMessageConsumptionStoreIntegrationTest {

    private static final Instant START = Instant.parse("2026-07-11T04:00:00Z");

    private JdbcTemplate jdbc;
    private MutableClock clock;
    private JdbcMessageConsumptionStore store;
    private JdbcDataSource dataSource;
    private DataSourceTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:mq_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        dataSource.setUser("sa");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        clock = new MutableClock(START);
        transactionManager = new DataSourceTransactionManager(dataSource);
        store = new JdbcMessageConsumptionStore(jdbc, transactionManager, clock);
    }

    @Test
    void tenConcurrentClaimsHaveExactlyOneWinner() throws Exception {
        int contenders = 10;
        var ready = new CountDownLatch(contenders);
        var start = new CountDownLatch(1);
        var futures = new ArrayList<java.util.concurrent.Future<MessageClaimResult>>();

        try (var executor = Executors.newFixedThreadPool(contenders)) {
            for (int index = 0; index < contenders; index++) {
                int ownerIndex = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("concurrency barrier timed out");
                    }
                    return store.claim(
                            "orders", "event-concurrent", owner(ownerIndex),
                            Duration.ofSeconds(30));
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            long claimed = 0L;
            for (var future : futures) {
                if (future.get(5, TimeUnit.SECONDS).status() == MessageClaimStatus.CLAIMED) {
                    claimed++;
                }
            }
            assertThat(claimed).isEqualTo(1L);
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM mq_consumption", Integer.class)).isEqualTo(1);
    }

    @Test
    void tenConcurrentDeliveriesProduceExactlyOneCommittedBusinessEffect() throws Exception {
        int deliveries = 10;
        var consumer = new IdempotentMessageConsumer(
                "orders", 3, Duration.ofSeconds(30), store, clock);
        var event = event("event-deliveries");
        var ready = new CountDownLatch(deliveries);
        var start = new CountDownLatch(1);
        var futures = new ArrayList<java.util.concurrent.Future<MessageConsumptionResult>>();

        try (var executor = Executors.newFixedThreadPool(deliveries)) {
            for (int index = 0; index < deliveries; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("concurrency barrier timed out");
                    }
                    return consumer.consume(event, 1, ignored -> jdbc.update(
                            "INSERT INTO mq_business_effect VALUES (?, ?)",
                            "event-deliveries", "once"));
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            long acknowledged = 0L;
            for (var future : futures) {
                MessageConsumptionResult result = future.get(5, TimeUnit.SECONDS);
                assertThat(result).isIn(
                        MessageConsumptionResult.ACKNOWLEDGED,
                        MessageConsumptionResult.DUPLICATE,
                        MessageConsumptionResult.RETRY);
                if (result == MessageConsumptionResult.ACKNOWLEDGED) {
                    acknowledged++;
                }
            }
            assertThat(acknowledged).isEqualTo(1L);
        }
        assertThat(count("mq_business_effect")).isEqualTo(1);
        assertThat(status("event-deliveries")).isEqualTo("SUCCEEDED");
    }

    @Test
    void handlerEffectAndSuccessCommitAtomicallyAndRollbackTogether() throws Exception {
        var first = claim("event-rollback", owner(1));

        assertThatThrownBy(() -> store.executeAndMarkSucceeded(first, () -> {
            jdbc.update("INSERT INTO mq_business_effect VALUES (?, ?)",
                    "event-rollback", "must-rollback");
            throw new RetryableMessageException("DEPENDENCY_TIMEOUT");
        })).isInstanceOf(RetryableMessageException.class);
        assertThat(count("mq_business_effect")).isZero();
        assertThat(status("event-rollback")).isEqualTo("PROCESSING");

        assertThat(store.releaseForRetry(first)).isTrue();
        var second = claim("event-rollback", owner(2));
        assertThat(store.executeAndMarkSucceeded(second, () -> jdbc.update(
                "INSERT INTO mq_business_effect VALUES (?, ?)",
                "event-rollback", "committed"))).isTrue();
        assertThat(count("mq_business_effect")).isEqualTo(1);
        assertThat(status("event-rollback")).isEqualTo("SUCCEEDED");
    }

    @Test
    void expiredClaimCanBeReacquiredAndStaleOwnerCannotMutateAnything() throws Exception {
        var stale = claim("event-stale", owner(1));
        clock.advance(Duration.ofSeconds(31));
        var current = store.claim(
                "orders", "event-stale", owner(2), Duration.ofSeconds(30))
                .claim().orElseThrow();

        assertThat(store.executeAndMarkSucceeded(stale, () -> jdbc.update(
                "INSERT INTO mq_business_effect VALUES (?, ?)", "event-stale", "stale")))
                .isFalse();
        assertThat(store.releaseForRetry(stale)).isFalse();
        assertThat(store.markDeadLettered(stale, deadLetter("event-stale"))).isFalse();
        assertThat(count("mq_business_effect")).isZero();
        assertThat(store.executeAndMarkSucceeded(current, () -> jdbc.update(
                "INSERT INTO mq_business_effect VALUES (?, ?)", "event-stale", "current")))
                .isTrue();
    }

    @Test
    void deadLetterAndTerminalStateCommitAtomically() {
        var claim = claim("event-dlq", owner(1));

        assertThat(store.markDeadLettered(claim, deadLetter("event-dlq"))).isTrue();
        assertThat(status("event-dlq")).isEqualTo("DEAD_LETTERED");
        assertThat(count("mq_dead_letter")).isEqualTo(1);
        assertThat(store.claim(
                "orders", "event-dlq", owner(2), Duration.ofSeconds(30)).status())
                .isEqualTo(MessageClaimStatus.DUPLICATE);
    }

    @Test
    void deadLetterInsertFailureRollsBackTerminalTransition() {
        var claim = claim("event-dlq-rollback", owner(1));
        jdbc.update("INSERT INTO mq_dead_letter VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "orders", "event-dlq-rollback", "ORDER_CREATED", 1, "order-1", "trace-1",
                1, "PREEXISTING_FAILURE", java.sql.Timestamp.from(START));

        assertThatThrownBy(() -> store.markDeadLettered(
                claim, deadLetter("event-dlq-rollback")))
                .isInstanceOf(MessageInfrastructureException.class);
        assertThat(status("event-dlq-rollback")).isEqualTo("PROCESSING");
        assertThat(count("mq_dead_letter")).isEqualTo(1);
    }

    @Test
    void duplicateKeyThenConcurrentReleaseNeverAcknowledgesMissingRow() {
        claim("event-release-race", owner(1));
        var raceStore = new JdbcMessageConsumptionStore(
                jdbc,
                transactionManager,
                clock,
                (consumerGroup, eventId) -> jdbc.update(
                        "DELETE FROM mq_consumption WHERE consumer_group = ? AND event_id = ?",
                        consumerGroup,
                        eventId));

        MessageClaimResult result = raceStore.claim(
                "orders", "event-release-race", owner(2), Duration.ofSeconds(30));

        assertThat(result.status()).isEqualTo(MessageClaimStatus.IN_PROGRESS);
        assertThat(count("mq_consumption")).isZero();
    }

    @Test
    void transactionManagerFailureIsInfrastructureAndRollsBackBusinessEffect() {
        var claim = claim("event-commit-failure", owner(1));
        var failingManager = new FailingCommitTransactionManager(dataSource);
        failingManager.setRollbackOnCommitFailure(true);
        var failingStore = new JdbcMessageConsumptionStore(jdbc, failingManager, clock);

        assertThatThrownBy(() -> failingStore.executeAndMarkSucceeded(claim, () -> jdbc.update(
                "INSERT INTO mq_business_effect VALUES (?, ?)",
                "event-commit-failure", "rollback")))
                .isInstanceOf(MessageInfrastructureException.class);
        assertThat(count("mq_business_effect")).isZero();
        assertThat(status("event-commit-failure")).isEqualTo("PROCESSING");
    }

    @Test
    void storeRejectsUnboundedLeaseUsingItsOwnClock() {
        assertThatThrownBy(() -> store.claim(
                "orders", "event-short", owner(1), Duration.ofMillis(999)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.claim(
                "orders", "event-long", owner(1), Duration.ofMinutes(16)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private MessageProcessingClaim claim(String eventId, String owner) {
        return store.claim("orders", eventId, owner, Duration.ofSeconds(30))
                .claim().orElseThrow();
    }

    private DeadLetterRecord deadLetter(String eventId) {
        return new DeadLetterRecord(
                eventId, "ORDER_CREATED", 1, "orders", "order-1", "trace-1",
                3, "UNEXPECTED_CONSUMER_FAILURE", clock.instant());
    }

    private String status(String eventId) {
        return jdbc.queryForObject(
                "SELECT status FROM mq_consumption WHERE consumer_group = 'orders' AND event_id = ?",
                String.class,
                eventId);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private static String owner(int index) {
        return "owner-" + String.format("%026d", index);
    }

    private static VersionedDomainEvent<TestPayload> event(String eventId) {
        return VersionedDomainEvent.of(
                new EventMetadata(
                        eventId,
                        "ORDER_CREATED",
                        1,
                        OffsetDateTime.parse("2026-07-11T12:00:00+08:00"),
                        "trace-1",
                        "order-service",
                        "order-1"),
                new TestPayload("order-1"));
    }

    private record TestPayload(String orderId) implements ImmutableEventPayload {
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private static final class FailingCommitTransactionManager
            extends DataSourceTransactionManager {

        private FailingCommitTransactionManager(JdbcDataSource dataSource) {
            super(dataSource);
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            throw new TransactionSystemException("simulated commit failure");
        }
    }
}
