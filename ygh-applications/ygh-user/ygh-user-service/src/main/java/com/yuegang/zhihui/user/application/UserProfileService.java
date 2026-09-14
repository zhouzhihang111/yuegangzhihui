package com.yuegang.zhihui.user.application;

import com.yuegang.zhihui.common.core.*;
import com.yuegang.zhihui.user.api.*;
import com.yuegang.zhihui.user.domain.UserProfileRepository;
import java.net.URI;
import java.time.ZoneId;
import java.util.Objects;
import java.util.regex.Pattern;

public final class UserProfileService {
    private static final Pattern PHONE = Pattern.compile("\\+?[0-9][0-9 -]{5,31}");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private final UserProfileRepository repository;
    public UserProfileService(UserProfileRepository repository) { this.repository = Objects.requireNonNull(repository); }
    public UserProfileView get(String userId) {
        long id = parse(userId);
        return repository.findByUserId(id).orElseGet(() -> repository.save(id,
                new UpdateUserProfileRequest("新用户", null, null, null, "zh-CN", "Asia/Shanghai", 0))
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_CONFLICT)));
    }
    public UserProfileView update(String userId, UpdateUserProfileRequest request) {
        validate(request);
        return repository.save(parse(userId), request)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_CONFLICT));
    }
    private static void validate(UpdateUserProfileRequest request) {
        Objects.requireNonNull(request);
        try { ZoneId.of(request.timezone()); }
        catch (RuntimeException invalid) { throw new BusinessException(ErrorCode.VALIDATION_ERROR); }
        if (request.avatarUrl() != null && !request.avatarUrl().isBlank()) {
            try {
                URI uri = URI.create(request.avatarUrl());
                if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost() == null) throw new IllegalArgumentException();
            } catch (RuntimeException invalid) { throw new BusinessException(ErrorCode.VALIDATION_ERROR); }
        }
        if (request.phone() != null && !request.phone().isBlank() && !PHONE.matcher(request.phone().trim()).matches())
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        if (request.email() != null && !request.email().isBlank() && !EMAIL.matcher(request.email().trim()).matches())
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
    private static long parse(String value) {
        try { long id = Long.parseLong(value); if (id <= 0) throw new NumberFormatException(); return id; }
        catch (NumberFormatException invalid) { throw new BusinessException(ErrorCode.UNAUTHENTICATED); }
    }
}
