package com.yuegang.zhihui.auth.domain;

public record RefreshRotationResult(RefreshRotationStatus status, long accountId) {
    public RefreshRotationResult {
        if (status == RefreshRotationStatus.ROTATED && accountId <= 0) {
            throw new IllegalArgumentException("rotated result requires accountId");
        }
    }
    public static RefreshRotationResult invalid() { return new RefreshRotationResult(RefreshRotationStatus.INVALID, 0); }
    public static RefreshRotationResult replay() { return new RefreshRotationResult(RefreshRotationStatus.REPLAY_DETECTED, 0); }
}
