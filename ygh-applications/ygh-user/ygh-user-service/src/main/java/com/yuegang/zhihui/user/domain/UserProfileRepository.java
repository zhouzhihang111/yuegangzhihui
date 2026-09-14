package com.yuegang.zhihui.user.domain;

import com.yuegang.zhihui.user.api.*;
import java.util.Optional;

public interface UserProfileRepository {
    Optional<UserProfileView> findByUserId(long userId);
    Optional<UserProfileView> save(long userId, UpdateUserProfileRequest request);
}
