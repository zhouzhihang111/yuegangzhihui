package com.yuegang.zhihui.auth.domain;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class PasswordPolicy {
    public static final int MIN_LENGTH = 15;
    public static final int MAX_LENGTH = 128;

    private final int minimumLength;
    private final int maximumLength;
    private final CompromisedPasswordChecker compromisedPasswordChecker;

    public PasswordPolicy(int minimumLength, int maximumLength, CompromisedPasswordChecker compromisedPasswordChecker) {
        if (minimumLength < 1 || maximumLength < minimumLength) {
            throw new IllegalArgumentException("password length bounds are invalid");
        }
        this.minimumLength = minimumLength;
        this.maximumLength = maximumLength;
        this.compromisedPasswordChecker = Objects.requireNonNull(
                compromisedPasswordChecker, "compromisedPasswordChecker must not be null");
    }

    public PasswordValidationResult validate(char[] password) {
        if (password == null) {
            return new PasswordValidationResult(Set.of(PasswordViolation.TOO_SHORT));
        }
        var violations = EnumSet.noneOf(PasswordViolation.class);
        int length = Character.codePointCount(password, 0, password.length);
        if (length < minimumLength) {
            violations.add(PasswordViolation.TOO_SHORT);
        }
        if (length > maximumLength) {
            violations.add(PasswordViolation.TOO_LONG);
        }
        for (char character : password) {
            if (Character.isISOControl(character)) {
                violations.add(PasswordViolation.CONTROL_CHARACTER);
                break;
            }
        }
        if (compromisedPasswordChecker.isCompromised(password)) {
            violations.add(PasswordViolation.COMMON_PASSWORD);
        }
        return new PasswordValidationResult(violations);
    }

}
