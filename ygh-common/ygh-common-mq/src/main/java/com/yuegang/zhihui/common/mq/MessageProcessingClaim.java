package com.yuegang.zhihui.common.mq;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.regex.Pattern;

/** Owner-scoped claim used to prevent a stale consumer from finalizing another attempt. */
public record MessageProcessingClaim(
        String consumerGroup,
        String eventId,
        @JsonIgnore String owner
) {

    private static final Pattern GROUP = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
    private static final Pattern OWNER = Pattern.compile("[A-Za-z0-9_-]{32,128}");

    public MessageProcessingClaim {
        if (consumerGroup == null || !GROUP.matcher(consumerGroup).matches()) {
            throw new IllegalArgumentException("consumerGroup is malformed");
        }
        if (eventId == null || eventId.isBlank() || eventId.length() > 128) {
            throw new IllegalArgumentException("eventId is malformed");
        }
        if (owner == null || !OWNER.matcher(owner).matches()) {
            throw new IllegalArgumentException("claim owner is malformed");
        }
    }

    @Override
    public String toString() {
        return "MessageProcessingClaim[consumerGroup=" + consumerGroup
                + ", eventId=" + eventId + ", owner=[REDACTED]]";
    }
}
