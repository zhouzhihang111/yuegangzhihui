package com.yuegang.zhihui.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtPrincipalMapperTest {

    private final JwtPrincipalMapper mapper = new JwtPrincipalMapper();

    @Test
    void mapsOnlyExplicitRolesAndPermissions() {
        Jwt jwt = jwtBuilder()
                .subject("user-1001")
                .claim("roles", List.of("ADMIN"))
                .claim("permissions", List.of("user:profile:read"))
                .build();

        var principal = mapper.map(jwt);

        assertThat(principal.userId()).isEqualTo("user-1001");
        assertThat(principal.roles()).containsExactly("ADMIN");
        assertThat(principal.permissions()).containsExactly("user:profile:read");
        assertThat(principal.hasPermission("system:permission:grant")).isFalse();
    }

    @Test
    void authenticationConverterUsesRolePrefixAndExactPermissionAuthorities() {
        Jwt jwt = jwtBuilder()
                .subject("employee-1001")
                .claim("roles", List.of("EMPLOYEE"))
                .claim("permissions", List.of("training:course:learn"))
                .build();
        var converter = new GatewaySecurityConfiguration()
                .gatewayJwtAuthenticationConverter(mapper);

        var authentication = converter.convert(jwt).block();

        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("employee-1001");
        assertThat(authentication.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactlyInAnyOrder("ROLE_EMPLOYEE", "PERM_training:course:learn");
    }

    @Test
    void permissionClaimCannotEscalateIntoRoleNamespace() {
        Jwt jwt = jwtBuilder()
                .subject("user-1001")
                .claim("permissions", List.of("ROLE_ADMIN"))
                .build();
        var converter = new GatewaySecurityConfiguration()
                .gatewayJwtAuthenticationConverter(mapper);

        var authentication = converter.convert(jwt).block();

        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .contains("PERM_ROLE_ADMIN")
                .doesNotContain("ROLE_ADMIN");
    }

    @Test
    void treatsMissingAuthorityClaimsAsEmpty() {
        var principal = mapper.map(jwtBuilder().subject("user-1001").build());

        assertThat(principal.roles()).isEmpty();
        assertThat(principal.permissions()).isEmpty();
    }

    @Test
    void rejectsMissingSubjectOrMalformedAuthorityClaims() {
        assertThatThrownBy(() -> mapper.map(null))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("must not be null");
        assertThatThrownBy(() -> mapper.map(jwtBuilder().build()))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("subject");
        assertThatThrownBy(() -> mapper.map(jwtBuilder()
                .subject("user-1001")
                .claim("roles", "ADMIN")
                .build()))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("roles");
        assertThatThrownBy(() -> mapper.map(jwtBuilder()
                .subject("user-1001")
                .claim("permissions", List.of("user:profile:read", 7))
                .build()))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("permissions");
        assertThatThrownBy(() -> mapper.map(jwtBuilder()
                .subject("unsafe subject")
                .build()))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("subject");
        assertThatThrownBy(() -> mapper.map(jwtBuilder()
                .subject("user-1001")
                .claim("roles", List.of("ADMIN,ROOT"))
                .build()))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("unsafe value");
    }

    @Test
    void normalizesDuplicatesAndRejectsUnboundedAuthorityClaims() {
        var principal = mapper.map(jwtBuilder()
                .subject("user-1001")
                .claim("roles", List.of("CUSTOMER", "EMPLOYEE", "CUSTOMER"))
                .build());
        assertThat(principal.roles()).containsExactlyInAnyOrder("CUSTOMER", "EMPLOYEE");

        Set<String> tooMany = IntStream.range(0, 129)
                .mapToObj(index -> "ROLE_" + index)
                .collect(Collectors.toSet());
        assertThatThrownBy(() -> mapper.map(jwtBuilder()
                .subject("user-1001")
                .claim("roles", tooMany)
                .build()))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("count limit");

        Set<String> oversized = IntStream.range(0, 40)
                .mapToObj(index -> "ROLE_" + index + "_" + "X".repeat(110))
                .collect(Collectors.toSet());
        assertThatThrownBy(() -> mapper.map(jwtBuilder()
                .subject("user-1001")
                .claim("roles", oversized)
                .build()))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("length limit");
    }

    private static Jwt.Builder jwtBuilder() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("opaque-test-value")
                .header("alg", "RS256")
                .issuer("https://auth.example.test")
                .audience(List.of("ygh-api"))
                .issuedAt(now.minusSeconds(5))
                .expiresAt(now.plusSeconds(60));
    }
}
