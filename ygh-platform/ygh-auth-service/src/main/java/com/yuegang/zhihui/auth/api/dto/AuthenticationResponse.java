package com.yuegang.zhihui.auth.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AuthenticationResponse(@NotBlank String userId, @NotNull @Valid TokenResponse tokens) { }
