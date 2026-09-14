package com.yuegang.zhihui.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SensitiveValueHasherTest {

    private static final byte[] PEPPER = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

    @Test
    void normalizesPrincipalAndNeverReturnsOrPrintsTheOriginalValue() {
        var hasher = new SensitiveValueHasher(PEPPER);

        String first = hasher.hashPrincipal("  Alice@Example.COM ");
        String second = hasher.hashPrincipal("alice@example.com");

        assertThat(first).isEqualTo(second).matches("[0-9a-f]{64}");
        assertThat(first).doesNotContain("alice", "example");
        assertThat(hasher.toString()).isEqualTo("SensitiveValueHasher[pepper=[REDACTED]]");
    }

    @Test
    void rejectsWeakPepperAndUnsafePrincipal() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SensitiveValueHasher("too-short".getBytes(StandardCharsets.US_ASCII)));
        var hasher = new SensitiveValueHasher(PEPPER);
        assertThatIllegalArgumentException().isThrownBy(() -> hasher.hashPrincipal(" "));
        assertThatIllegalArgumentException().isThrownBy(() -> hasher.hashPrincipal("a\n@example.com"));
    }
}
