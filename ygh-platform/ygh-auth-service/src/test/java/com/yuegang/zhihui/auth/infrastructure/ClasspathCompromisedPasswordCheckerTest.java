package com.yuegang.zhihui.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ClasspathCompromisedPasswordCheckerTest {

    @Test
    void loadsVersionedIntegrityCheckedDatasetAndMatchesWithoutPlaintextResource() {
        var checker = new ClasspathCompromisedPasswordChecker();

        assertThat(checker.datasetVersion()).isEqualTo("SecLists-2026.1-xato-top-100000");
        assertThat(checker.isCompromised("passwordpassword".toCharArray())).isTrue();
        assertThat(checker.isCompromised("PASSWORDPASSWORD".toCharArray())).isTrue();
        assertThat(checker.isCompromised("ＰＡＳＳＷＯＲＤＰＡＳＳＷＯＲＤ".toCharArray())).isFalse();
        assertThat(checker.isCompromised("correct horse battery staple 企业专用".toCharArray())).isFalse();
    }

    @Test
    void missingOrIntegrityMismatchedDatasetFailsClosedAtConstruction() {
        assertThatThrownBy(() -> new ClasspathCompromisedPasswordChecker("/missing.bin", 1, "00".repeat(32)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unavailable");
        assertThatThrownBy(() -> new ClasspathCompromisedPasswordChecker(
                ClasspathCompromisedPasswordChecker.DEFAULT_RESOURCE,
                ClasspathCompromisedPasswordChecker.EXPECTED_ENTRIES,
                "00".repeat(32)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("integrity");
    }
}
