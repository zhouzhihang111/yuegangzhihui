package com.yuegang.zhihui.user.api;

import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.web.TraceIdResolver;
import com.yuegang.zhihui.user.application.UserProfileService;
import com.yuegang.zhihui.user.security.TrustedUserContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me")
public final class UserProfileController {
    private final UserProfileService profiles;
    private final TrustedUserContextResolver users;
    public UserProfileController(UserProfileService profiles, TrustedUserContextResolver users) {
        this.profiles = profiles; this.users = users;
    }
    @GetMapping public ApiResponse<UserProfileView> get(HttpServletRequest request) {
        return ApiResponse.success(profiles.get(users.resolve(request).userId()), TraceIdResolver.resolve(request));
    }
    @PutMapping public ApiResponse<UserProfileView> update(
            @Valid @RequestBody UpdateUserProfileRequest body, HttpServletRequest request) {
        return ApiResponse.success(profiles.update(users.resolve(request).userId(), body), TraceIdResolver.resolve(request));
    }
}
