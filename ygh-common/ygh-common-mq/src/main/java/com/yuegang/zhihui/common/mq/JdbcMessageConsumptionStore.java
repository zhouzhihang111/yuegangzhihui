package com.yuegang.zhihui.common.mq;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.DateTimeException;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** JDBC reference implementation proving durable claims and local transaction atomicity. */
public final class JdbcMessageConsumptionStore implements MessageConsumptionStore {

    private static final String PROCESSING = "PROCESSING";
    private static final String SUCCEEDED = "SUCCEEDED";
    private static final String DEAD_LETTERED = "DEAD_LETTERED";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final DuplicateClaimObserver duplicateClaimObserver;

    public JdbcMessageConsumptionStore(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this(jdbc, transactionManager, clock, (consumerGroup, eventId) -> { });
    }

    JdbcMessageConsumptionStore(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            Clock clock,
            DuplicateClaimObserver duplicateClaimObserver
    ) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
        this.transaction = new TransactionTemplate(Objects.requireNonNull(
                transactionManager, "transactionManager must not be null"));
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.duplicateClaimObserver = Objects.requireNonNull(
                duplicateClaimObserver, "duplicateClaimObserver must not be null");
    }

    @Override
    public MessageClaimResult claim(
            String consumerGroup,
            String eventId,
            String owner,
            Duration lease
    ) {
        requireLease(lease);
        MessageProcessingClaim claim = new MessageProcessingClaim(consumerGroup, eventId, owner);
        Instant now = clock.instant();
        Instant leaseUntil;
        try {
            leaseUntil = now.plus(lease);
        } catch (DateTimeException | ArithmeticException exception) {
            throw new IllegalArgumentException("lease exceeds the supported time range", exception);
        }
        try {
            MessageClaimResult result = transaction.execute(status -> doClaim(claim, now, leaseUntil));
            return Objects.requireNonNull(result, "claim transaction returned null");
        } catch (RuntimeException exception) {
            throw infrastructure("claim failed", exception);
        }
    }

    @Override
    public boolean executeAndMarkSucceeded(
            MessageProcessingClaim claim,
            MessageBusinessOperation businessOperation
    ) throws Exception {
        Objects.requireNonNull(claim, "claim must not be null");
        Objects.requireNonNull(businessOperation, "businessOperation must not be null");
        try {
            Boolean completed = transaction.execute(status -> {
                ConsumptionRow row = lockRow(claim.consumerGroup(), claim.eventId());
                if (!isCurrentClaim(row, claim, clock.instant())) {
                    return false;
                }
                try {
                    businessOperation.execute();
                } catch (Exception failure) {
                    throw new BusinessOperationFailure(failure);
                }
                int changed = jdbc.update(
                        "UPDATE mq_consumption SET status = ?, owner = NULL, lease_until = NULL, "
                                + "updated_at = ? WHERE consumer_group = ? AND event_id = ? "
                                + "AND status = ? AND owner = ?",
                        SUCCEEDED,
                        Timestamp.from(clock.instant()),
                        claim.consumerGroup(),
                        claim.eventId(),
                        PROCESSING,
                        claim.owner());
                if (changed != 1) {
                    throw new IllegalStateException("claim changed inside locked transaction");
                }
                return true;
            });
            return Boolean.TRUE.equals(completed);
        } catch (BusinessOperationFailure failure) {
            throw failure.original();
        } catch (MessageInfrastructureException infrastructure) {
            throw infrastructure;
        } catch (RuntimeException exception) {
            throw infrastructure("completion failed", exception);
        }
    }

    @Override
    public boolean releaseForRetry(MessageProcessingClaim claim) {
        Objects.requireNonNull(claim, "claim must not be null");
        try {
            return jdbc.update(
                    "DELETE FROM mq_consumption WHERE consumer_group = ? AND event_id = ? "
                            + "AND status = ? AND owner = ?",
                    claim.consumerGroup(), claim.eventId(), PROCESSING, claim.owner()) == 1;
        } catch (MessageInfrastructureException infrastructure) {
            throw infrastructure;
        } catch (RuntimeException exception) {
            throw infrastructure("retry release failed", exception);
        }
    }

    @Override
    public boolean markDeadLettered(MessageProcessingClaim claim, DeadLetterRecord record) {
        Objects.requireNonNull(claim, "claim must not be null");
        Objects.requireNonNull(record, "record must not be null");
        if (!claim.consumerGroup().equals(record.consumerGroup())
                || !claim.eventId().equals(record.eventId())) {
            throw new IllegalArgumentException("dead-letter record does not match claim");
        }
        try {
            Boolean completed = transaction.execute(status -> {
                ConsumptionRow row = lockRow(claim.consumerGroup(), claim.eventId());
                if (!isCurrentClaim(row, claim, clock.instant())) {
                    return false;
                }
                jdbc.update(
                        "INSERT INTO mq_dead_letter (consumer_group, event_id, event_type, "
                                + "event_version, business_key, trace_id, delivery_attempt, "
                                + "failure_code, failed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        record.consumerGroup(),
                        record.eventId(),
                        record.eventType(),
                        record.eventVersion(),
                        record.businessKey(),
                        record.traceId(),
                        record.deliveryAttempt(),
                        record.failureCode(),
                        Timestamp.from(record.failedAt()));
                int changed = jdbc.update(
                        "UPDATE mq_consumption SET status = ?, owner = NULL, lease_until = NULL, "
                                + "updated_at = ? WHERE consumer_group = ? AND event_id = ? "
                                + "AND status = ? AND owner = ?",
                        DEAD_LETTERED,
                        Timestamp.from(clock.instant()),
                        claim.consumerGroup(),
                        claim.eventId(),
                        PROCESSING,
                        claim.owner());
                if (changed != 1) {
                    throw new IllegalStateException("claim changed inside locked transaction");
                }
                return true;
            });
            return Boolean.TRUE.equals(completed);
        } catch (MessageInfrastructureException infrastructure) {
            throw infrastructure;
        } catch (RuntimeException exception) {
            throw infrastructure("dead-letter persistence failed", exception);
        }
    }

    private MessageClaimResult doClaim(
            MessageProcessingClaim claim,
            Instant now,
            Instant leaseUntil
    ) {
        try {
            jdbc.update(
                    "INSERT INTO mq_consumption (consumer_group, event_id, status, owner, "
                            + "lease_until, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    claim.consumerGroup(),
                    claim.eventId(),
                    PROCESSING,
                    claim.owner(),
                    Timestamp.from(leaseUntil),
                    Timestamp.from(now),
                    Timestamp.from(now));
            return MessageClaimResult.claimed(claim);
        } catch (DuplicateKeyException duplicate) {
            duplicateClaimObserver.afterDuplicate(claim.consumerGroup(), claim.eventId());
            ConsumptionRow existing = findRow(claim.consumerGroup(), claim.eventId());
            if (existing == null) {
                // A retry release may delete the row between duplicate detection and this read.
                // Never acknowledge such a race as a duplicate; let the broker redeliver.
                return MessageClaimResult.inProgress();
            }
            if (SUCCEEDED.equals(existing.status()) || DEAD_LETTERED.equals(existing.status())) {
                return MessageClaimResult.duplicate();
            }
            if (existing.leaseUntil() != null && existing.leaseUntil().isAfter(now)) {
                return MessageClaimResult.inProgress();
            }
            int changed = jdbc.update(
                    "UPDATE mq_consumption SET owner = ?, lease_until = ?, updated_at = ? "
                            + "WHERE consumer_group = ? AND event_id = ? AND status = ? "
                            + "AND lease_until <= ?",
                    claim.owner(),
                    Timestamp.from(leaseUntil),
                    Timestamp.from(now),
                    claim.consumerGroup(),
                    claim.eventId(),
                    PROCESSING,
                    Timestamp.from(now));
            return changed == 1
                    ? MessageClaimResult.claimed(claim)
                    : MessageClaimResult.inProgress();
        }
    }

    private ConsumptionRow findRow(String consumerGroup, String eventId) {
        return jdbc.query(
                "SELECT status, owner, lease_until FROM mq_consumption "
                        + "WHERE consumer_group = ? AND event_id = ?",
                resultSet -> resultSet.next()
                        ? new ConsumptionRow(
                                resultSet.getString("status"),
                                resultSet.getString("owner"),
                                toInstant(resultSet.getTimestamp("lease_until")))
                        : null,
                consumerGroup,
                eventId);
    }

    private ConsumptionRow lockRow(String consumerGroup, String eventId) {
        return jdbc.query(
                "SELECT status, owner, lease_until FROM mq_consumption "
                        + "WHERE consumer_group = ? AND event_id = ? FOR UPDATE",
                resultSet -> resultSet.next()
                        ? new ConsumptionRow(
                                resultSet.getString("status"),
                                resultSet.getString("owner"),
                                toInstant(resultSet.getTimestamp("lease_until")))
                        : null,
                consumerGroup,
                eventId);
    }

    private static boolean isCurrentClaim(
            ConsumptionRow row,
            MessageProcessingClaim claim,
            Instant now
    ) {
        return row != null
                && PROCESSING.equals(row.status())
                && claim.owner().equals(row.owner())
                && row.leaseUntil() != null
                && row.leaseUntil().isAfter(now);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static MessageInfrastructureException infrastructure(
            String operation,
            RuntimeException cause
    ) {
        return new MessageInfrastructureException(operation, cause);
    }

    private record ConsumptionRow(String status, String owner, Instant leaseUntil) {
    }

    private static final class BusinessOperationFailure extends RuntimeException {
        private final Exception original;

        private BusinessOperationFailure(Exception original) {
            super(null, original, false, false);
            this.original = original;
        }

        private Exception original() {
            return original;
        }
    }

    private static void requireLease(Duration lease) {
        Objects.requireNonNull(lease, "lease must not be null");
        if (lease.compareTo(Duration.ofSeconds(1)) < 0
                || lease.compareTo(Duration.ofMinutes(15)) > 0) {
            throw new IllegalArgumentException("lease must be between 1 second and 15 minutes");
        }
    }

    @FunctionalInterface
    interface DuplicateClaimObserver {
        void afterDuplicate(String consumerGroup, String eventId);
    }
}
