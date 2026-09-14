package com.yuegang.zhihui.auth.domain;

import java.time.Duration;

public interface CaptchaChallengeStore {
    void save(String challengeId, String answerHash, Duration ttl);
    boolean consume(String challengeId, String presentedAnswerHash);
}
