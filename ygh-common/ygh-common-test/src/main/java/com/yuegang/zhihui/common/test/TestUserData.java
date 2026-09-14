package com.yuegang.zhihui.common.test;

/** Synthetic identity using reserved domains and an explicit test namespace. */
public record TestUserData(String userId, String username, String email, String displayName) {

    public TestUserData {
        if (userId == null || !userId.matches("test-user-[0-9]+-[0-9]+")) {
            throw new IllegalArgumentException("userId must use the synthetic test namespace");
        }
        if (username == null || !username.matches("test_user_[0-9]+_[0-9]+")) {
            throw new IllegalArgumentException("username must use the synthetic test namespace");
        }
        if (email == null || !email.endsWith("@example.test")) {
            throw new IllegalArgumentException("email must use the reserved example.test domain");
        }
        if (displayName == null || !displayName.startsWith("测试用户")) {
            throw new IllegalArgumentException("displayName must be explicitly synthetic");
        }
    }

    @Override
    public String toString() {
        return "TestUserData[userId=" + userId + ", username=" + username
                + ", email=[REDACTED], displayName=" + displayName + ']';
    }
}
