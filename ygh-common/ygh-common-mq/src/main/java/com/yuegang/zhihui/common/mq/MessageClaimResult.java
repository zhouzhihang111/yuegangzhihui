package com.yuegang.zhihui.common.mq;

import java.util.Objects;
import java.util.Optional;

/** Claim result with a capability present only for the winning consumer. */
public record MessageClaimResult(MessageClaimStatus status, Optional<MessageProcessingClaim> claim) {

    public MessageClaimResult {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(claim, "claim must not be null");
        if ((status == MessageClaimStatus.CLAIMED) != claim.isPresent()) {
            throw new IllegalArgumentException("only CLAIMED may contain a claim");
        }
    }

    public static MessageClaimResult claimed(MessageProcessingClaim claim) {
        return new MessageClaimResult(MessageClaimStatus.CLAIMED, Optional.of(claim));
    }

    public static MessageClaimResult duplicate() {
        return new MessageClaimResult(MessageClaimStatus.DUPLICATE, Optional.empty());
    }

    public static MessageClaimResult inProgress() {
        return new MessageClaimResult(MessageClaimStatus.IN_PROGRESS, Optional.empty());
    }
}
