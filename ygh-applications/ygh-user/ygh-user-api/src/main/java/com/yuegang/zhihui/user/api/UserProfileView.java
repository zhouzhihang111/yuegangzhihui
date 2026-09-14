package com.yuegang.zhihui.user.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Public immutable projection; persistence entities never cross the service boundary. */
public record UserProfileView(
        @NotBlank String userId,
        @NotBlank @Size(max = 80) String displayName,
        @Size(max = 512) String avatarUrl,
        String phone,
        String email,
        String locale,
        String timezone,
        long version
) {
    public UserProfileView(String userId, String displayName, String avatarUrl, long version) {
        this(userId, displayName, avatarUrl, null, null, "zh-CN", "Asia/Shanghai", version);
    }

    public UserProfileView {
        if (userId == null || !userId.matches("[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException("userId must be a positive decimal identifier");
        }
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
    }

    @Override
    public String toString() {
        return "UserProfileView[userId=" + userId + ", displayName=" + displayName + ", avatarUrl=" + avatarUrl
                + ", phone=<redacted>, email=<redacted>, locale=" + locale + ", timezone=" + timezone
                + ", version=" + version + "]";
    }
}
