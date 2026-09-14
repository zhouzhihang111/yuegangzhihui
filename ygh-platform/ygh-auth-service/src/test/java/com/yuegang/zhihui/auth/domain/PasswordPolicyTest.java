package com.yuegang.zhihui.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuegang.zhihui.auth.infrastructure.ClasspathCompromisedPasswordChecker;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy(
            PasswordPolicy.MIN_LENGTH, PasswordPolicy.MAX_LENGTH,
            new ClasspathCompromisedPasswordChecker());

    @Test
    void acceptsLongUnicodePassphrasesWithoutCompositionRules() {
        var result = policy.validate("这是 一条 足够长的 企业口令 passphrase".toCharArray());
        assertThat(result.valid()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void rejectsShortOverlongControlAndCommonPasswordsWithoutEchoingThem() {
        assertThat(policy.validate("short password".toCharArray()).violations())
                .containsExactly(PasswordViolation.TOO_SHORT);
        assertThat(policy.validate("x".repeat(129).toCharArray()).violations())
                .containsExactly(PasswordViolation.TOO_LONG);
        assertThat(policy.validate("valid length but\u0000bad".toCharArray()).violations())
                .contains(PasswordViolation.CONTROL_CHARACTER);
        var common = policy.validate("passwordpassword".toCharArray());
        assertThat(common.violations()).contains(PasswordViolation.COMMON_PASSWORD);
        assertThat(policy.validate("PASSWORDPASSWORD".toCharArray()).violations())
                .contains(PasswordViolation.COMMON_PASSWORD);
        assertThat(common.toString()).doesNotContain("passwordpassword");
    }
}
