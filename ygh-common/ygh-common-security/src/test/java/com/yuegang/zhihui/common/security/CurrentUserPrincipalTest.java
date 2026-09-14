package com.yuegang.zhihui.common.security;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserPrincipalTest {

    @Test
    void shouldKeepStringUserIdAndDefensivelyCopyAuthorities() {
        Set<String> roles = new LinkedHashSet<>(Set.of("CUSTOMER"));
        Set<String> permissions = new LinkedHashSet<>(Set.of("user:address:read"));

        CurrentUserPrincipal principal = new CurrentUserPrincipal("9007199254740993", roles, permissions);
        roles.add("ADMIN");
        permissions.add("user:address:manage:any");

        assertThat(principal.userId()).isEqualTo("9007199254740993");
        assertThat(principal.roles()).containsExactly("CUSTOMER");
        assertThat(principal.permissions()).containsExactly("user:address:read");
        assertThatThrownBy(() -> principal.roles().add("ADMIN"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> principal.permissions().add("user:address:manage:any"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldMatchRolesAndPermissionsExactly() {
        CurrentUserPrincipal principal = new CurrentUserPrincipal(
                "user-1001",
                Set.of("KNOWLEDGE_REVIEWER"),
                Set.of("knowledge:document:review", "user:address:read")
        );

        assertThat(principal.hasRole("KNOWLEDGE_REVIEWER")).isTrue();
        assertThat(principal.hasRole("knowledge_reviewer")).isFalse();
        assertThat(principal.hasPermission("knowledge:document:review")).isTrue();
        assertThat(principal.hasPermission("knowledge:document")).isFalse();
        assertThat(principal.hasPermission("KNOWLEDGE:DOCUMENT:REVIEW")).isFalse();
    }
}
