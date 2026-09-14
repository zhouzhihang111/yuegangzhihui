package com.yuegang.zhihui.common.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuegang.zhihui.common.core.EventMetadata;
import com.yuegang.zhihui.common.core.ImmutableEventPayload;
import com.yuegang.zhihui.common.core.VersionedDomainEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class IdempotentMessageConsumerTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-11T04:00:00Z"), ZoneOffset.UTC);

    @Test
    void duplicateDeliveryTenTimesProducesOneBusinessEffect() {
        var store = new FakeStore();
        var consumer = consumer(store, 3);
        var effects = new AtomicInteger();
        var event = event("event-1");

        assertThat(consumer.consume(event, 1, ignored -> effects.incrementAndGet()))
                .isEqualTo(MessageConsumptionResult.ACKNOWLEDGED);
        for (int delivery = 0; delivery < 9; delivery++) {
            assertThat(consumer.consume(event, 1, ignored -> effects.incrementAndGet()))
                    .isEqualTo(MessageConsumptionResult.DUPLICATE);
        }
        assertThat(effects).hasValue(1);
    }

    @Test
    void activeClaimRetriesWithoutExecutingHandler() {
        var store = new FakeStore();
        store.states.put("orders:event-2", "PROCESSING");
        var effects = new AtomicInteger();

        assertThat(consumer(store, 3).consume(
                event("event-2"), 1, ignored -> effects.incrementAndGet()))
                .isEqualTo(MessageConsumptionResult.RETRY);
        assertThat(effects).hasValue(0);
    }

    @Test
    void retryableFailureReleasesClaimAndLaterDeliveryCanSucceed() {
        var store = new FakeStore();
        var consumer = consumer(store, 3);
        var attempts = new AtomicInteger();
        var event = event("event-3");

        assertThat(consumer.consume(event, 1, ignored -> {
            attempts.incrementAndGet();
            throw new RetryableMessageException("BROKER_DEPENDENCY_TIMEOUT");
        })).isEqualTo(MessageConsumptionResult.RETRY);
        assertThat(consumer.consume(event, 2, ignored -> attempts.incrementAndGet()))
                .isEqualTo(MessageConsumptionResult.ACKNOWLEDGED);
        assertThat(attempts).hasValue(2);
    }

    @Test
    void nonRetryableFailureIsAtomicallyRecordedAsDeadLetter() {
        var store = new FakeStore();
        var event = event("event-4");

        assertThat(consumer(store, 3).consume(event, 1, ignored -> {
            throw new NonRetryableMessageException("UNSUPPORTED_EVENT_VERSION");
        })).isEqualTo(MessageConsumptionResult.DEAD_LETTERED);

        assertThat(store.deadLetters).containsKey("orders:event-4");
        var record = store.deadLetters.get("orders:event-4");
        assertThat(record.failureCode()).isEqualTo("UNSUPPORTED_EVENT_VERSION");
        assertThat(record).extracting(DeadLetterRecord::eventId, DeadLetterRecord::eventType,
                        DeadLetterRecord::consumerGroup, DeadLetterRecord::deliveryAttempt)
                .containsExactly("event-4", "ORDER_CREATED", "orders", 1);
        assertThat(consumer(store, 3).consume(event, 2, ignored -> {
            throw new AssertionError("terminal event must not run again");
        })).isEqualTo(MessageConsumptionResult.DUPLICATE);
    }

    @Test
    void finalRetryBecomesSanitizedDeadLetter() {
        var store = new FakeStore();

        assertThat(consumer(store, 3).consume(event("event-5"), 3, ignored -> {
            throw new IllegalStateException("raw exception detail must not appear");
        })).isEqualTo(MessageConsumptionResult.DEAD_LETTERED);

        assertThat(store.deadLetters.get("orders:event-5").failureCode())
                .isEqualTo("UNEXPECTED_CONSUMER_FAILURE")
                .doesNotContain("detail");
    }

    @Test
    void staleClaimCannotAcknowledgeBusinessEffect() {
        var store = new FakeStore();
        store.failCompletion = true;

        assertThat(consumer(store, 3).consume(event("event-6"), 1, ignored -> { }))
                .isEqualTo(MessageConsumptionResult.RETRY);
    }

    @Test
    void validatesDeliveryAttemptAndConsumerConfiguration() {
        var store = new FakeStore();
        assertThatThrownBy(() -> consumer(store, 3).consume(event("event-7"), 0, ignored -> { }))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdempotentMessageConsumer(
                "Orders", 3, Duration.ofSeconds(30), store, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void claimOwnerCapabilityIsRedactedFromLogsAndJson() {
        var claim = new MessageProcessingClaim(
                "orders", "event-8", "owner-00000000000000000000000001");

        assertThat(claim.toString()).contains("[REDACTED]").doesNotContain(claim.owner());
        assertThat(JsonMapper.builder().build().writeValueAsString(claim))
                .doesNotContain(claim.owner())
                .contains("\"consumerGroup\"")
                .contains("\"eventId\"");
    }

    @Test
    void interruptedHandlerRestoresInterruptAndRequestsRetry() {
        var store = new FakeStore();
        try {
            assertThat(consumer(store, 3).consume(event("event-9"), 1, ignored -> {
                throw new InterruptedException("stop");
            })).isEqualTo(MessageConsumptionResult.RETRY);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(store.deadLetters).isEmpty();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void infrastructureFailureNeverBecomesDeadLetterEvenAtFinalAttempt() {
        var store = new FakeStore();

        assertThat(consumer(store, 3).consume(event("event-10"), 3, ignored -> {
            throw new MessageInfrastructureException("database unavailable", new IllegalStateException());
        })).isEqualTo(MessageConsumptionResult.RETRY);
        assertThat(store.deadLetters).isEmpty();
    }

    @Test
    void claimReleaseAndDeadLetterInfrastructureFailuresAllRequestRetry() {
        var claimFailure = new FakeStore();
        claimFailure.failClaimInfrastructure = true;
        assertThat(consumer(claimFailure, 3).consume(event("event-11"), 3, ignored -> { }))
                .isEqualTo(MessageConsumptionResult.RETRY);

        var releaseFailure = new FakeStore();
        releaseFailure.failReleaseInfrastructure = true;
        assertThat(consumer(releaseFailure, 3).consume(event("event-12"), 1, ignored -> {
            throw new RetryableMessageException("DEPENDENCY_TIMEOUT");
        })).isEqualTo(MessageConsumptionResult.RETRY);

        var deadLetterFailure = new FakeStore();
        deadLetterFailure.failDeadLetterInfrastructure = true;
        assertThat(consumer(deadLetterFailure, 3).consume(event("event-13"), 3, ignored -> {
            throw new NonRetryableMessageException("INVALID_EVENT");
        })).isEqualTo(MessageConsumptionResult.RETRY);
        assertThat(deadLetterFailure.deadLetters).isEmpty();
    }

    @Test
    void secureClaimOwnersAreUniqueAndHighEntropy() {
        var generator = new SecureMessageClaimOwnerGenerator();
        var owners = new java.util.HashSet<String>();
        for (int index = 0; index < 1000; index++) {
            owners.add(generator.generate());
        }
        assertThat(owners).hasSize(1000).allMatch(owner -> owner.matches("[A-Za-z0-9_-]{32}"));
    }

    private static IdempotentMessageConsumer consumer(FakeStore store, int maxAttempts) {
        return new IdempotentMessageConsumer(
                "orders", maxAttempts, Duration.ofSeconds(30), store, CLOCK);
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
                        "order-1001"),
                new TestPayload("order-1001"));
    }

    private record TestPayload(String orderId) implements ImmutableEventPayload {
    }

    private static final class FakeStore implements MessageConsumptionStore {
        private final Map<String, String> states = new HashMap<>();
        private final Map<String, DeadLetterRecord> deadLetters = new HashMap<>();
        private boolean failCompletion;
        private boolean failClaimInfrastructure;
        private boolean failReleaseInfrastructure;
        private boolean failDeadLetterInfrastructure;

        @Override
        public MessageClaimResult claim(
                String consumerGroup,
                String eventId,
                String owner,
                Duration lease
        ) {
            if (failClaimInfrastructure) {
                throw new MessageInfrastructureException("claim unavailable", new IllegalStateException());
            }
            String key = consumerGroup + ':' + eventId;
            String state = states.get(key);
            if ("SUCCEEDED".equals(state) || "DEAD_LETTERED".equals(state)) {
                return MessageClaimResult.duplicate();
            }
            if ("PROCESSING".equals(state)) {
                return MessageClaimResult.inProgress();
            }
            states.put(key, "PROCESSING");
            return MessageClaimResult.claimed(new MessageProcessingClaim(
                    consumerGroup, eventId, owner));
        }

        @Override
        public boolean executeAndMarkSucceeded(
                MessageProcessingClaim claim,
                MessageBusinessOperation businessOperation
        ) throws Exception {
            if (failCompletion) {
                return false;
            }
            businessOperation.execute();
            states.put(key(claim), "SUCCEEDED");
            return true;
        }

        @Override
        public boolean releaseForRetry(MessageProcessingClaim claim) {
            if (failReleaseInfrastructure) {
                throw new MessageInfrastructureException("release unavailable", new IllegalStateException());
            }
            states.remove(key(claim));
            return true;
        }

        @Override
        public boolean markDeadLettered(MessageProcessingClaim claim, DeadLetterRecord record) {
            if (failDeadLetterInfrastructure) {
                throw new MessageInfrastructureException("dead letter unavailable", new IllegalStateException());
            }
            deadLetters.put(key(claim), record);
            states.put(key(claim), "DEAD_LETTERED");
            return true;
        }

        private static String key(MessageProcessingClaim claim) {
            return claim.consumerGroup() + ':' + claim.eventId();
        }
    }
}
