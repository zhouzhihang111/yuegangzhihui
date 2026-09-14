package com.yuegang.zhihui.common.security;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class PermissionContractTest {

    @Test
    void shouldExposePermissionAnnotationAtRuntime() throws NoSuchMethodException {
        Method method = ProtectedOperations.class.getDeclaredMethod("reviewKnowledge");

        RequiresPermission annotation = method.getAnnotation(RequiresPermission.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("knowledge:document:review");
    }

    @Test
    void shouldUseDomainOwnershipCheckerWithoutGrantingImplicitAdminBypass() {
        ResourceAccessGuard guard = new ResourceAccessGuard();
        CurrentUserPrincipal principal = new CurrentUserPrincipal("reviewer-1", Set.of("ADMIN"), Set.of());
        ResourceOwnershipChecker<String> checker = (currentUser, documentId) ->
                currentUser.userId().equals("owner-of-" + documentId);

        assertThatCode(() -> guard.requireOwnerOrPermission(
                new CurrentUserPrincipal("owner-of-doc-1", Set.of(), Set.of()),
                "doc-1",
                checker,
                "knowledge:document:manage:any"
        )).doesNotThrowAnyException();

        org.junit.jupiter.api.Assertions.assertThrows(
                com.yuegang.zhihui.common.core.BusinessException.class,
                () -> guard.requireOwnerOrPermission(
                        principal,
                        "doc-1",
                        checker,
                        "knowledge:document:manage:any"
                )
        );
    }

    private static final class ProtectedOperations {

        @RequiresPermission("knowledge:document:review")
        private void reviewKnowledge() {
        }
    }
}
