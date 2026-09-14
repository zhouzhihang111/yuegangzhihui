package com.yuegang.zhihui.auth.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

public record LoginAttempt(
        Long accountId,
        String principalHash,
        String clientIpHash,
        LoginAttemptResult result,
        String failureReason,
        Instant occurredAt,
        String traceId
) {
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    public LoginAttempt {
        if (accountId != null && accountId <= 0) throw new IllegalArgumentException("accountId must be positive");
        if (principalHash == null || !HASH.matcher(principalHash).matches()) {
            throw new IllegalArgumentException("principalHash must be lowercase SHA-256 length");
        }
        if (clientIpHash == null || !HASH.matcher(clientIpHash).matches()) {
            throw new IllegalArgumentException("clientIpHash must be a lowercase HMAC-SHA256 digest");
        }
        Objects.requireNonNull(result, "result must not be null");
        if (failureReason != null && !SAFE_CODE.matcher(failureReason).matches()) {
            throw new IllegalArgumentException("failureReason must be a safe code");
        }
        if (result == LoginAttemptResult.SUCCESS && failureReason != null) {
            throw new IllegalArgumentException("successful attempts cannot have a failure reason");
        }
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (traceId == null || !SAFE_TRACE_ID.matcher(traceId).matches()) {
            throw new IllegalArgumentException("traceId is unsafe");
        }
    }

    @Override
    public String toString() {
        return "LoginAttempt[accountId=" + accountId + ", principalHash=[REDACTED], clientIpHash=[REDACTED], result="
                + result + ", failureReason=" + failureReason + ", occurredAt=" + occurredAt
                + ", traceId=" + traceId + "]";
    }
}
