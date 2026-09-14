package com.yuegang.zhihui.user.api;

import jakarta.validation.constraints.*;

public record UpdateUserProfileRequest(
        @NotBlank @Size(max = 80) String displayName,
        @Size(max = 512) String avatarUrl,
        @Pattern(regexp = "\\+?[0-9][0-9 -]{5,31}") String phone,
        @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = "[a-z]{2}(?:-[A-Z]{2})?") String locale,
        @NotBlank @Size(max = 64) String timezone,
        @PositiveOrZero long version
) {
    public UpdateUserProfileRequest(String displayName, String avatarUrl, String locale, String timezone, long version) {
        this(displayName, avatarUrl, null, null, locale, timezone, version);
    }

    @Override
    public String toString() {
        return "UpdateUserProfileRequest[displayName=" + displayName + ", avatarUrl=" + avatarUrl
                + ", phone=<redacted>, email=<redacted>, locale=" + locale + ", timezone=" + timezone
                + ", version=" + version + "]";
    }
}
