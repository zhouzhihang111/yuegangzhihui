package com.yuegang.zhihui.auth.application;

import static org.assertj.core.api.Assertions.*;
import com.yuegang.zhihui.auth.api.dto.*;
import com.yuegang.zhihui.auth.domain.*;
import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.common.redis.SessionStateStore;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AuthenticationLifecycleUseCaseTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-12T00:00:00Z"), ZoneOffset.UTC);

    @Test void captchaIsOneTimeAndRegistrationIssuesTokens() {
        var store = new MemoryCaptchaStore();
        var hasher = new SensitiveValueHasher(new byte[32]);
        var captcha = new CaptchaService(store, hasher, CLOCK, () -> "ABC234");
        CaptchaResponse challenge = captcha.create();
        byte[] image = Base64.getDecoder().decode(challenge.imageBase64());
        assertThat(challenge.mimeType()).isEqualTo("image/png");
        assertThat(image).startsWith(0x89, 0x50, 0x4e, 0x47);
        assertThat(new String(image, StandardCharsets.ISO_8859_1)).doesNotContain("ABC234");
        String answer = "ABC234";

        var accounts = new MemoryAccounts();
        var refreshRepository = new MemoryRefreshRepository();
        var registration = new RegistrationUseCase(accounts,
                new PasswordPolicy(15, 128, new CompromisedPasswordChecker() {
                    public boolean isCompromised(char[] password) { return false; }
                    public String datasetVersion() { return "test"; }
                }), Argon2PasswordHasher.owaspMinimum(),
                captcha, new SnowflakeIdGenerator(3, CLOCK), principal -> access(),
                new OpaqueRefreshTokenService(refreshRepository, CLOCK, Duration.ofDays(14)),
                new NoOpSessions(), CLOCK);
        var response = registration.register(new RegisterRequest(" Customer@Example.com ",
                "enterprise passphrase 2026", "enterprise passphrase 2026",
                challenge.challengeId(), answer, true));

        assertThat(response.userId()).isEqualTo(Long.toString(accounts.account.userId()));
        assertThat(response.tokens().accessToken()).isEqualTo("access-token");
        assertThat(accounts.account.passwordDigest().hash()).doesNotContain("enterprise passphrase 2026");
        assertThatThrownBy(() -> captcha.verify(challenge.challengeId(), answer))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test void refreshRotatesOnceDetectsReplayAndLogoutIsIdempotent() {
        var repository = new MemoryRefreshRepository();
        var refresh = new OpaqueRefreshTokenService(repository, CLOCK, Duration.ofDays(14));
        RefreshTokenPair initial = refresh.issueInitial(41);
        var accounts = new MemoryAccounts();
        accounts.account = account(41, 51);
        var lifecycle = new TokenLifecycleUseCase(refresh, accounts, principal -> access(), new NoOpSessions(), CLOCK);

        TokenResponse rotated = lifecycle.refresh(new RefreshTokenRequest(initial.value(), "browser-1"));
        assertThat(rotated.refreshToken()).isNotEqualTo(initial.value());
        assertThatThrownBy(() -> lifecycle.refresh(new RefreshTokenRequest(initial.value(), "browser-1")))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        assertThat(repository.familyRevoked).isTrue();
        var access = new AuthenticatedAccessToken(41, "logout-jti", CLOCK.instant().plusSeconds(900));
        assertThat(lifecycle.logout(new LogoutRequest(rotated.refreshToken()), access).completed()).isTrue();
        assertThat(lifecycle.logout(new LogoutRequest(rotated.refreshToken()), access).completed()).isTrue();
    }

    private static AccessToken access() {
        return new AccessToken("access-token", UUID.randomUUID().toString(), CLOCK.instant().plusSeconds(900));
    }
    private static LoginAccount account(long accountId, long userId) {
        return new LoginAccount(accountId, userId, "CUSTOMER", AccountStatus.ACTIVE,
                new PasswordDigest("$argon2id$v=19$m=19456,t=2,p=1$AAAAAAAAAAAAAAAAAAAAAA$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "ARGON2ID", 1));
    }

    private static final class MemoryCaptchaStore implements CaptchaChallengeStore {
        private final Map<String,String> values = new HashMap<>();
        public void save(String id, String hash, Duration ttl) { values.put(id, hash); }
        public boolean consume(String id, String hash) { return Objects.equals(values.remove(id), hash); }
    }
    private static final class MemoryAccounts implements LoginAccountRepository {
        LoginAccount account;
        public Optional<LoginAccount> findByPrincipal(String principal) { return Optional.ofNullable(account); }
        public Optional<LoginAccount> findByAccountId(long id) { return Optional.ofNullable(account).filter(a -> a.accountId() == id); }
        public LoginAccount create(long accountId, long userId, String principal, String type, PasswordDigest digest) {
            account = new LoginAccount(accountId, userId, type, AccountStatus.ACTIVE, digest); return account;
        }
    }
    private static final class MemoryRefreshRepository implements RefreshTokenRepository {
        String currentHash; String previousHash; long accountId; boolean familyRevoked;
        public void insertInitial(long accountId, String family, NewRefreshToken token) {
            this.accountId = accountId; currentHash = token.tokenHash();
        }
        public RefreshRotationResult rotate(String hash, NewRefreshToken replacement, Instant now) {
            if (familyRevoked) return RefreshRotationResult.invalid();
            if (Objects.equals(hash, previousHash)) { familyRevoked = true; return RefreshRotationResult.replay(); }
            if (!Objects.equals(hash, currentHash)) return RefreshRotationResult.invalid();
            previousHash = currentHash; currentHash = replacement.tokenHash();
            return new RefreshRotationResult(RefreshRotationStatus.ROTATED, accountId);
        }
        public void revokeFamilyByTokenHash(String hash, Instant now, String reason) {
            if (Objects.equals(hash, currentHash) || Objects.equals(hash, previousHash)) familyRevoked = true;
        }
    }
    private static final class NoOpSessions implements SessionStateStore {
        public void register(long accountId, String jti, Instant expires, Instant now) { }
        public void revoke(long accountId, String jti, Instant expires, Instant now) { }
        public void disableAccount(long accountId) { }
        public void enableAccount(long accountId) { }
    }
}
