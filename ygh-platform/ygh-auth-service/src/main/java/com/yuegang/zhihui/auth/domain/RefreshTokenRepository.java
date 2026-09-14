package com.yuegang.zhihui.auth.domain;

import java.time.Instant;

public interface RefreshTokenRepository {
    void insertInitial(long accountId, String family, NewRefreshToken token);
    RefreshRotationResult rotate(String presentedHash, NewRefreshToken replacement, Instant now);
    void revokeFamilyByTokenHash(String presentedHash, Instant now, String reason);
}
