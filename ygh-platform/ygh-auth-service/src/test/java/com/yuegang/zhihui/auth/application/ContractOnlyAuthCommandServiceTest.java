package com.yuegang.zhihui.auth.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuegang.zhihui.auth.api.dto.*;
import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import org.junit.jupiter.api.Test;

class ContractOnlyAuthCommandServiceTest {

    private final ContractOnlyAuthCommandService service = new ContractOnlyAuthCommandService();

    @Test
    void everyUnimplementedUseCaseFailsClosedAsDependencyUnavailable() {
        assertUnavailable(() -> service.register(null));
        assertUnavailable(() -> service.login(null, null));
        assertUnavailable(() -> service.refresh(null));
        assertUnavailable(() -> service.logout(null, null));
        assertUnavailable(service::captcha);
        assertUnavailable(() -> service.requestPasswordReset(null));
        assertUnavailable(() -> service.confirmPasswordReset(null));
    }

    private static void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE));
    }
}
