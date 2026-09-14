package com.yuegang.zhihui.system.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SystemSecretCipherTest {
    @Test
    void encryptsWithRandomNonceAndRejectsWrongKey() {
        var cipher = new SystemSecretCipher("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8));
        var first = cipher.encrypt("ark-api-key-sensitive");
        var second = cipher.encrypt("ark-api-key-sensitive");

        assertThat(first.ciphertext()).doesNotContain("ark-api-key-sensitive").isNotEqualTo(second.ciphertext());
        assertThat(cipher.decrypt(first.ciphertext(), first.nonce())).isEqualTo("ark-api-key-sensitive");

        var wrong = new SystemSecretCipher("abcdefghijklmnopqrstuvwxyz123456".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> wrong.decrypt(first.ciphertext(), first.nonce()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requiresAes256MasterKey() {
        assertThatThrownBy(() -> new SystemSecretCipher(new byte[16]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
