package com.yuegang.zhihui.common.mq;

import java.time.Duration;

/**
 * Durable consumer idempotency boundary.
 *
 * <p>Implementations must compare the claim owner on every mutation. The business operation and
 * SUCCEEDED transition must commit in the same local database transaction. If the claim is stale,
 * implementations must return {@code false} without invoking the business operation. If the
 * operation throws, its transaction and the success transition must both roll back. Dead-letter
 * record and DEAD_LETTERED transition must also commit atomically.</p>
 */
public interface MessageConsumptionStore {

    MessageClaimResult claim(
            String consumerGroup,
            String eventId,
            String owner,
            Duration lease
    );

    boolean executeAndMarkSucceeded(
            MessageProcessingClaim claim,
            MessageBusinessOperation businessOperation
    ) throws Exception;

    boolean releaseForRetry(MessageProcessingClaim claim);

    boolean markDeadLettered(MessageProcessingClaim claim, DeadLetterRecord record);
}
