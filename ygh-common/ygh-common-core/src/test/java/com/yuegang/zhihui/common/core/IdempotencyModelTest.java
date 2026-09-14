package com.yuegang.zhihui.common.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class IdempotencyModelTest {

    private static final OffsetDateTime REQUESTED_AT =
            OffsetDateTime.of(2026, 7, 11, 15, 0, 0, 0, ZoneOffset.ofHours(8));

    @Test
    void requestContextIdentifiesOneCallerOperationAndRequestBody() {
        var context = new IdempotencyRequestContext(
                "idem-wallet-pay-001",
                "WALLET_PAY",
                "user-9007199254740993",
                "sha256:0123456789abcdef",
                REQUESTED_AT);

        assertThat(context.idempotencyKey()).isEqualTo("idem-wallet-pay-001");
        assertThat(context.operation()).isEqualTo("WALLET_PAY");
        assertThat(context.subjectId()).isEqualTo("user-9007199254740993");
        assertThat(context.requestFingerprint()).isEqualTo("sha256:0123456789abcdef");
        assertThat(context.requestedAt()).isEqualTo(REQUESTED_AT);
        assertThat(context.requestedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(8));
    }

    @Test
    void requestContextRejectsMissingIdentityOrFingerprint() {
        assertThatThrownBy(() -> new IdempotencyRequestContext(
                        " ", "WALLET_PAY", "user-1", "sha256:abc", REQUESTED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdempotencyRequestContext(
                        "idem-1", "WALLET_PAY", "user-1", " ", REQUESTED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdempotencyRequestContext(
                        "idem-1", "WALLET_PAY", "user-1", "sha256:abc", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void completedResultCanBeReplayedWithTheOriginalBusinessValue() {
        var completedAt = REQUESTED_AT.plusSeconds(1);
        var result = IdempotencyResult.completed(
                "idem-wallet-pay-001", "wallet-flow-001", completedAt);

        assertThat(result.idempotencyKey()).isEqualTo("idem-wallet-pay-001");
        assertThat(result.status()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(result.value()).isEqualTo("wallet-flow-001");
        assertThat(result.completedAt()).isEqualTo(completedAt);
        assertThat(result.replayable()).isTrue();
    }

    @Test
    void inProgressResultCannotPretendToBeReplayable() {
        var result = IdempotencyResult.inProgress("idem-wallet-pay-001");

        assertThat(result.status()).isEqualTo(IdempotencyStatus.IN_PROGRESS);
        assertThat(result.value()).isNull();
        assertThat(result.completedAt()).isNull();
        assertThat(result.replayable()).isFalse();
    }

    @Test
    void completedResultRequiresKeyValueAndOffsetTimestamp() {
        assertThatThrownBy(() -> IdempotencyResult.completed(" ", "flow-1", REQUESTED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IdempotencyResult.completed("idem-1", null, REQUESTED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IdempotencyResult.completed("idem-1", "flow-1", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
