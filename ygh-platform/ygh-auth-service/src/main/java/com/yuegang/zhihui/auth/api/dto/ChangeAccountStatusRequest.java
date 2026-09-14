package com.yuegang.zhihui.auth.api.dto;

import jakarta.validation.constraints.NotNull;

public record ChangeAccountStatusRequest(@NotNull AccountStatusCommand status, long version, String reason) {
    public enum AccountStatusCommand { ACTIVE, DISABLED }
}
