package com.yuegang.zhihui.common.mq;

import com.yuegang.zhihui.common.core.DomainEvent;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

/** Framework-neutral at-least-once consumer state machine. */
public final class IdempotentMessageConsumer {

    private static final Pattern GROUP = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
    private static final String UNEXPECTED_FAILURE = "UNEXPECTED_CONSUMER_FAILURE";

    private final String consumerGroup;
    private final int maxAttempts;
    private final Duration claimLease;
    private final MessageConsumptionStore store;
    private final Clock clock;
    private final MessageClaimOwnerGenerator ownerGenerator;

    public IdempotentMessageConsumer(
            String consumerGroup,
            int maxAttempts,
            Duration claimLease,
            MessageConsumptionStore store,
            Clock clock
    ) {
        if (consumerGroup == null || !GROUP.matcher(consumerGroup).matches()) {
            throw new IllegalArgumentException("consumerGroup is malformed");
        }
        if (maxAttempts < 1 || maxAttempts > 100) {
            throw new IllegalArgumentException("maxAttempts must be between 1 and 100");
        }
        Objects.requireNonNull(claimLease, "claimLease must not be null");
        if (claimLease.compareTo(Duration.ofSeconds(1)) < 0
                || claimLease.compareTo(Duration.ofMinutes(15)) > 0) {
            throw new IllegalArgumentException("claimLease must be between 1 second and 15 minutes");
        }
        this.consumerGroup = consumerGroup;
        this.maxAttempts = maxAttempts;
        this.claimLease = claimLease;
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.ownerGenerator = new SecureMessageClaimOwnerGenerator();
    }

    public <T> MessageConsumptionResult consume(
            DomainEvent<T> event,
            int deliveryAttempt,
            MessageHandler<T> handler
    ) {
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(handler, "handler must not be null");
        if (deliveryAttempt < 1) {
            throw new IllegalArgumentException("deliveryAttempt must be at least 1");
        }

        MqEnvelopePolicy.validate(event);
        try {
            MessageClaimResult claimResult = store.claim(
                    consumerGroup,
                    event.eventId(),
                    ownerGenerator.generate(),
                    claimLease);
            return switch (claimResult.status()) {
                case DUPLICATE -> MessageConsumptionResult.DUPLICATE;
                case IN_PROGRESS -> MessageConsumptionResult.RETRY;
                case CLAIMED -> processClaim(
                        event, deliveryAttempt, handler,
                        validatedClaim(claimResult, event.eventId()));
            };
        } catch (MessageInfrastructureException infrastructureFailure) {
            return MessageConsumptionResult.RETRY;
        }
    }

    private <T> MessageConsumptionResult processClaim(
            DomainEvent<T> event,
            int deliveryAttempt,
            MessageHandler<T> handler,
            MessageProcessingClaim claim
    ) {
        try {
            boolean completed = store.executeAndMarkSucceeded(
                    claim, () -> handler.handle(event));
            return completed
                    ? MessageConsumptionResult.ACKNOWLEDGED
                    : MessageConsumptionResult.RETRY;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return MessageConsumptionResult.RETRY;
        } catch (MessageInfrastructureException infrastructureFailure) {
            return MessageConsumptionResult.RETRY;
        } catch (Exception failure) {
            boolean terminal = failure instanceof NonRetryableMessageException
                    || deliveryAttempt >= maxAttempts;
            if (!terminal) {
                store.releaseForRetry(claim);
                return MessageConsumptionResult.RETRY;
            }
            var record = new DeadLetterRecord(
                    event.eventId(),
                    event.eventType(),
                    event.eventVersion(),
                    consumerGroup,
                    event.businessKey(),
                    event.traceId(),
                    deliveryAttempt,
                    failureCode(failure),
                    clock.instant());
            return store.markDeadLettered(claim, record)
                    ? MessageConsumptionResult.DEAD_LETTERED
                    : MessageConsumptionResult.RETRY;
        }
    }

    private MessageProcessingClaim validatedClaim(MessageClaimResult result, String eventId) {
        MessageProcessingClaim claim = result.claim().orElseThrow(
                () -> new IllegalStateException("CLAIMED result has no claim"));
        if (!consumerGroup.equals(claim.consumerGroup()) || !eventId.equals(claim.eventId())) {
            throw new IllegalStateException("store returned a claim for another message");
        }
        return claim;
    }

    private static String failureCode(Exception failure) {
        return failure instanceof MessageHandlingException messageFailure
                ? messageFailure.failureCode()
                : UNEXPECTED_FAILURE;
    }
}
