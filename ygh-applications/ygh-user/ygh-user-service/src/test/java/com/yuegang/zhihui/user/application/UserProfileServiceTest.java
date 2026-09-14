package com.yuegang.zhihui.user.application;

import static org.assertj.core.api.Assertions.*;
import com.yuegang.zhihui.common.core.*;
import com.yuegang.zhihui.user.api.*;
import com.yuegang.zhihui.user.domain.UserProfileRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UserProfileServiceTest {
    @Test void createsReadsAndUpdatesOnlyWithCurrentVersion() {
        var repository = new MemoryRepository();
        var service = new UserProfileService(repository);
        var created = service.update("42", request("Alice", null, 0));
        assertThat(created.version()).isZero();
        assertThat(service.get("42")).isEqualTo(created);
        var updated = service.update("42", request("Alice Chen", "https://cdn.example/avatar.png", 0));
        assertThat(updated.version()).isEqualTo(1);
        assertThatThrownBy(() -> service.update("42", request("Stale", null, 0)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.BUSINESS_CONFLICT));
    }
    @Test void rejectsMissingInvalidTimezoneAndUnsafeAvatar() {
        var service = new UserProfileService(new MemoryRepository());
        assertThat(service.get("42")).extracting(UserProfileView::displayName).isEqualTo("新用户");
        assertThatThrownBy(() -> service.update("42", new UpdateUserProfileRequest("Alice", null, "zh-CN", "Mars/Base", 0)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.update("42", request("Alice", "file:///secret", 0)))
                .isInstanceOf(BusinessException.class);
    }
    private static UpdateUserProfileRequest request(String name, String avatar, long version) {
        return new UpdateUserProfileRequest(name, avatar, "zh-CN", "Asia/Shanghai", version);
    }
    private static final class MemoryRepository implements UserProfileRepository {
        UserProfileView value;
        public Optional<UserProfileView> findByUserId(long id) { return Optional.ofNullable(value); }
        public Optional<UserProfileView> save(long id, UpdateUserProfileRequest request) {
            if (value == null) {
                if (request.version() != 0) return Optional.empty();
                value = new UserProfileView(Long.toString(id), request.displayName(), request.avatarUrl(), 0);
            } else {
                if (value.version() != request.version()) return Optional.empty();
                value = new UserProfileView(Long.toString(id), request.displayName(), request.avatarUrl(), value.version() + 1);
            }
            return Optional.of(value);
        }
    }
}
