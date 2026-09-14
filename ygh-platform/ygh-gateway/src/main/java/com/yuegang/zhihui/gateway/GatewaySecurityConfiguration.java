package com.yuegang.zhihui.gateway;

import java.util.LinkedHashSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.core.convert.converter.Converter;
import reactor.core.publisher.Mono;

/** Fail-closed reactive resource-server security for all Gateway API routes. */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
class GatewaySecurityConfiguration {

    @Bean
    ReactiveJwtDecoder gatewayJwtDecoder(
            @Value("${ygh.security.jwt.issuer}") String issuer,
            @Value("${ygh.security.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${ygh.security.jwt.audience}") String audience
    ) {
        var decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(GatewayJwtValidators.create(issuer, audience));
        return decoder;
    }

    @Bean
    Converter<Jwt, Mono<AbstractAuthenticationToken>> gatewayJwtAuthenticationConverter(
            JwtPrincipalMapper principalMapper
    ) {
        return jwt -> {
            var principal = principalMapper.map(jwt);
            var authorities = new LinkedHashSet<SimpleGrantedAuthority>();
            principal.roles().stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .forEach(authorities::add);
            principal.permissions().stream()
                    .map(permission -> new SimpleGrantedAuthority("PERM_" + permission))
                    .forEach(authorities::add);
            return Mono.just(new JwtAuthenticationToken(jwt, authorities, principal.userId()));
        };
    }

    @Bean
    SecurityWebFilterChain gatewaySecurityWebFilterChain(
            ServerHttpSecurity http,
            GatewaySecurityErrorWriter errorWriter,
            Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter
    ) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(
                                "/actuator/health",
                                "/actuator/health/liveness",
                                "/actuator/health/readiness",
                                "/actuator/info",
                                "/livez",
                                "/readyz",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**").permitAll()
                        .pathMatchers(HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/password-reset/request",
                                "/api/v1/auth/password-reset/confirm").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/auth/captcha").permitAll()
                        .pathMatchers(HttpMethod.GET,
                                "/api/v1/products",
                                "/api/v1/products/**",
                                "/api/v1/product-categories",
                                "/api/v1/product-brands",
                                "/api/v1/knowledge/documents",
                                "/api/v1/knowledge/documents/**",
                                "/api/v1/knowledge/search").permitAll()
                        .pathMatchers(
                                "/api/v1/organization/**",
                                "/api/v1/training/**").hasAnyRole("EMPLOYEE", "ADMIN")
                        .pathMatchers(
                                "/api/v1/auth/admin/**",
                                "/api/v1/admin/**",
                                "/api/v1/system/**",
                                "/api/v1/roles/**",
                                "/api/v1/permissions/**").hasRole("ADMIN")
                        .pathMatchers("/api/v1/**").authenticated()
                        .anyExchange().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((exchange, ignored) ->
                                errorWriter.unauthenticated(exchange))
                        .accessDeniedHandler((exchange, ignored) ->
                                errorWriter.accessDenied(exchange)))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint((exchange, ignored) ->
                                errorWriter.unauthenticated(exchange))
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .build();
    }
}
