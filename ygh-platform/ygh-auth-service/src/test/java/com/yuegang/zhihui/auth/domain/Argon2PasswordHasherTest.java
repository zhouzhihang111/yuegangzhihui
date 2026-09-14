package com.yuegang.zhihui.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class Argon2PasswordHasherTest {

    private final Argon2PasswordHasher hasher = Argon2PasswordHasher.owaspMinimum();

    @Test
    void createsSaltedArgon2idDigestsAndVerifiesWithoutRetainingPlaintext() {
        char[] raw = "correct horse battery staple 企业".toCharArray();
        var first = hasher.hash(raw);
        var second = hasher.hash(raw);

        assertThat(first.algorithm()).isEqualTo("ARGON2ID");
        assertThat(first.version()).isEqualTo(1);
        assertThat(first.hash()).startsWith("$argon2id$v=19$m=19456,t=2,p=1$");
        assertThat(second.hash()).isNotEqualTo(first.hash());
        assertThat(hasher.matches(raw, first)).isTrue();
        assertThat(hasher.matches("wrong password value".toCharArray(), first)).isFalse();
        assertThat(first.toString()).doesNotContain(first.hash(), "correct horse");
        assertThat(raw).containsExactly("correct horse battery staple 企业".toCharArray());
    }

    @Test
    void malformedOrLegacyDigestsFailClosedAndRequestUpgrade() {
        var malformed = new PasswordDigest("not-a-hash", "ARGON2ID", 1);
        var legacy = new PasswordDigest("$2a$10$legacy", "BCRYPT", 1);

        assertThat(hasher.matches("password".toCharArray(), malformed)).isFalse();
        assertThat(hasher.needsUpgrade(malformed)).isTrue();
        assertThat(hasher.needsUpgrade(legacy)).isTrue();
    }

    @Test
    void hostileResourceParametersAndOversizedFieldsFailBeforeArgon2Execution() {
        var hostile = new PasswordDigest(
                "$argon2id$v=19$m=999999,t=99,p=99$AAAAAAAAAAAAAAAAAAAAAA$"
                        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                "ARGON2ID", 1);
        var oversized = new PasswordDigest("$argon2id$v=19$m=8,t=1,p=1$" + "A".repeat(600), "ARGON2ID", 1);

        assertThat(hasher.matches("irrelevant password".toCharArray(), hostile)).isFalse();
        assertThat(hasher.matches("irrelevant password".toCharArray(), oversized)).isFalse();
        assertThat(hasher.needsUpgrade(hostile)).isTrue();
    }

    @Test
    void constructorRejectsParametersOutsideTheVerificationResourceEnvelope() {
        assertThatThrownBy(() -> new Argon2PasswordHasher(16, 32, 1, 32 * 1024 + 1, 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Argon2PasswordHasher(16, 32, 1, 19 * 1024, 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Argon2PasswordHasher(
                16, 32, 3, 19 * 1024, 2, 2, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
