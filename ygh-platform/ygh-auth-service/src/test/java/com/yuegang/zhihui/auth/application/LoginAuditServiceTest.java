package com.yuegang.zhihui.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuegang.zhihui.auth.domain.LoginAttempt;
import com.yuegang.zhihui.auth.domain.LoginAttemptRepository;
import com.yuegang.zhihui.auth.domain.LoginAttemptResult;
import com.yuegang.zhihui.auth.domain.SensitiveValueHasher;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class LoginAuditServiceTest {

    @Test
    void storesOnlyKeyedPrincipalHashCanonicalAddressAndSafeReason() throws Exception {
        var stored = new ArrayList<LoginAttempt>();
        LoginAttemptRepository repository = stored::add;
        var service = new LoginAuditService(
                repository,
                new SensitiveValueHasher(
                        "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII)),
                Clock.fixed(Instant.parse("2026-07-12T00:00:00Z"), ZoneOffset.UTC));

        service.record(
                42L,
                "Alice@Example.com",
                InetAddress.getByName("2001:db8::1"),
                LoginAttemptResult.INVALID_CREDENTIALS,
                "BAD_CREDENTIALS",
                "trace-123");

        assertThat(stored).singleElement().satisfies(attempt -> {
            assertThat(attempt.accountId()).isEqualTo(42L);
            assertThat(attempt.principalHash()).matches("[0-9a-f]{64}")
                    .doesNotContain("alice", "example");
            assertThat(attempt.clientIpHash()).matches("[0-9a-f]{64}");
            assertThat(attempt.clientIpHash()).doesNotContain("2001", "db8");
            assertThat(attempt.result()).isEqualTo(LoginAttemptResult.INVALID_CREDENTIALS);
            assertThat(attempt.failureReason()).isEqualTo("BAD_CREDENTIALS");
            assertThat(attempt.occurredAt()).isEqualTo("2026-07-12T00:00:00Z");
            assertThat(attempt.traceId()).isEqualTo("trace-123");
            assertThat(attempt.toString()).doesNotContain("Alice", "2001:db8");
        });
    }

    @Test
    void supportsUnknownAccountWithoutInventingAnIdentifier() throws Exception {
        var stored = new ArrayList<LoginAttempt>();
        var service = new LoginAuditService(
                stored::add,
                new SensitiveValueHasher(
                        "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII)),
                Clock.systemUTC());

        service.record(null, "unknown@example.com", InetAddress.getByName("192.0.2.10"),
                LoginAttemptResult.INVALID_CREDENTIALS, "ACCOUNT_NOT_FOUND", "trace-unknown");

        assertThat(stored).singleElement().extracting(LoginAttempt::accountId).isNull();
    }
}
