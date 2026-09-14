package com.yuegang.zhihui.common.security;

import com.yuegang.zhihui.common.core.BusinessException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResourceAccessGuardTest {

    private static final String MANAGE_ANY_ADDRESS = "user:address:manage:any";

    private final ResourceAccessGuard guard = new ResourceAccessGuard();

    @Test
    void shouldAllowOwnerToAccessOwnResource() {
        CurrentUserPrincipal principal = principal("user-1001", Set.of("CUSTOMER"), Set.of());

        assertThatCode(() -> guard.requireOwnerOrPermission(
                principal,
                "user-1001",
                MANAGE_ANY_ADDRESS
        )).doesNotThrowAnyException();
    }

    @Test
    void shouldAllowAdministratorOnlyWhenExplicitBypassPermissionIsPresent() {
        CurrentUserPrincipal principal = principal(
                "admin-1001",
                Set.of("ADMIN"),
                Set.of(MANAGE_ANY_ADDRESS)
        );

        assertThatCode(() -> guard.requireOwnerOrPermission(
                principal,
                "user-1001",
                MANAGE_ANY_ADDRESS
        )).doesNotThrowAnyException();
    }

    @Test
    void shouldNotTreatAdministratorRoleAsImplicitOwnershipBypass() {
        CurrentUserPrincipal principal = principal("admin-1001", Set.of("ADMIN"), Set.of());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> guard.requireOwnerOrPermission(principal, "user-1001", MANAGE_ANY_ADDRESS),
                "ADMIN role alone must not bypass ownership"
        );

        assertThat(stableErrorCode(exception)).isEqualTo("PERMISSION_DENIED");
    }

    @Test
    void shouldRejectNonOwnerWithoutPermissionUsingStableBusinessError() {
        CurrentUserPrincipal principal = principal(
                "user-2002",
                Set.of("CUSTOMER"),
                Set.of("user:address:read")
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> guard.requireOwnerOrPermission(principal, "user-1001", MANAGE_ANY_ADDRESS),
                "Cross-owner access must require an explicit permission"
        );

        assertThat(stableErrorCode(exception)).isEqualTo("PERMISSION_DENIED");
    }

    private static CurrentUserPrincipal principal(String userId, Set<String> roles, Set<String> permissions) {
        return new CurrentUserPrincipal(userId, roles, permissions);
    }

    /**
     * BusinessException 的错误载体属于 common-core 契约；测试兼容 code/getCode 与
     * errorCode/getErrorCode 两种只读访问形式，但最终对外稳定值必须是 PERMISSION_DENIED。
     */
    private static String stableErrorCode(BusinessException exception) {
        Object value = invokeFirstNoArg(exception, "code", "getCode", "errorCode", "getErrorCode");
        if (value instanceof String code) {
            return code;
        }
        Object nested = invokeFirstNoArg(value, "code", "getCode", "name");
        return String.valueOf(nested);
    }

    private static Object invokeFirstNoArg(Object target, String... methodNames) {
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
                // Try the next established Java accessor form.
            } catch (IllegalAccessException | InvocationTargetException exception) {
                throw new AssertionError("Cannot read business error code through " + methodName, exception);
            }
        }
        throw new AssertionError("BusinessException must expose a stable error code");
    }
}
