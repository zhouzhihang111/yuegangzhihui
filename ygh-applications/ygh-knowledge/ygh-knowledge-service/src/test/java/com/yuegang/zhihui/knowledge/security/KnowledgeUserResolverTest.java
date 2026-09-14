package com.yuegang.zhihui.knowledge.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.security.InternalUserContextSignature;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class KnowledgeUserResolverTest {
    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    @Test void requiresValidSignatureAndAdminRoleWhenRequested(){var resolver=new KnowledgeUserResolver(SECRET);assertThat(resolver.resolve(signed(List.of("USER")),false)).isEqualTo(42);assertThat(resolver.resolve(signed(List.of("ADMIN")),true)).isEqualTo(42);assertThatThrownBy(()->resolver.resolve(signed(List.of("USER")),true)).isInstanceOf(BusinessException.class);var broken=signed(List.of("ADMIN"));broken.removeHeader("X-YGH-User-Context-Signature");assertThatThrownBy(()->resolver.resolve(broken,true)).isInstanceOf(BusinessException.class);}
    @Test void grantsOnlyPublicVisibilityToAnonymousRequestsAndRejectsPartialIdentity(){
        var resolver = new KnowledgeUserResolver(SECRET);
        var anonymous = new MockHttpServletRequest("GET", "/api/v1/knowledge/search");
        assertThat(resolver.resolveContext(anonymous).anonymous()).isTrue();
        assertThat(resolver.resolveContext(anonymous).knowledgeVisibilities()).containsExactly("PUBLIC");
        anonymous.addHeader("X-YGH-Roles", "ADMIN");
        assertThatThrownBy(() -> resolver.resolveContext(anonymous)).isInstanceOf(BusinessException.class);
    }
    private static MockHttpServletRequest signed(List<String>roles){Instant now=Instant.now();var r=new MockHttpServletRequest("POST","/api/v1/admin/knowledge");var m=new InternalUserContextSignature.Metadata("42",roles,List.of(),"trace","request","POST",r.getRequestURI(),now);var s=new InternalUserContextSignature(SECRET,Clock.systemUTC(),Duration.ofSeconds(30));r.addHeader("X-YGH-User-Id","42");r.addHeader("X-YGH-Roles",String.join(",",roles));r.addHeader("X-Trace-Id","trace");r.addHeader("X-Request-Id","request");r.addHeader("X-YGH-User-Context-Timestamp",Long.toString(now.toEpochMilli()));r.addHeader("X-YGH-User-Context-Signature",s.sign(m));return r;}
}
