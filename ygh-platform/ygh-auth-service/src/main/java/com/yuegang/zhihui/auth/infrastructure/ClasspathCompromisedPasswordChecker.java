package com.yuegang.zhihui.auth.infrastructure;

import com.yuegang.zhihui.auth.domain.CompromisedPasswordChecker;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

public final class ClasspathCompromisedPasswordChecker implements CompromisedPasswordChecker {
    public static final String DEFAULT_RESOURCE = "/security/common-passwords-sha256.bin";
    public static final String DATASET_VERSION = "SecLists-2026.1-xato-top-100000";
    public static final int EXPECTED_ENTRIES = 96_518;
    public static final String EXPECTED_SHA256 = "d27cc6628a51c24255521284ce4c7d50bca1365a8224a42d0b92420df78c8ba6";
    private static final int DIGEST_LENGTH = 32;

    private final byte[] sortedDigests;

    public ClasspathCompromisedPasswordChecker() {
        this(DEFAULT_RESOURCE, EXPECTED_ENTRIES, EXPECTED_SHA256);
    }

    ClasspathCompromisedPasswordChecker(String resource, int expectedEntries, String expectedSha256) {
        Objects.requireNonNull(resource, "resource must not be null");
        try (InputStream input = ClasspathCompromisedPasswordChecker.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("compromised-password dataset is unavailable");
            }
            sortedDigests = input.readAllBytes();
        } catch (IOException readFailure) {
            throw new IllegalStateException("compromised-password dataset cannot be read", readFailure);
        }
        if (sortedDigests.length != Math.multiplyExact(expectedEntries, DIGEST_LENGTH)
                || !MessageDigest.isEqual(sha256(sortedDigests), HexFormat.of().parseHex(expectedSha256))) {
            Arrays.fill(sortedDigests, (byte) 0);
            throw new IllegalStateException("compromised-password dataset integrity check failed");
        }
        for (int entry = 1; entry < expectedEntries; entry++) {
            if (compare(sortedDigests, (entry - 1) * DIGEST_LENGTH,
                    sortedDigests, entry * DIGEST_LENGTH) >= 0) {
                Arrays.fill(sortedDigests, (byte) 0);
                throw new IllegalStateException("compromised-password dataset is not strictly sorted");
            }
        }
    }

    @Override
    public boolean isCompromised(char[] password) {
        Objects.requireNonNull(password, "password must not be null");
        char[] normalized = password.clone();
        for (int index = 0; index < normalized.length; index++) {
            if (normalized[index] >= 'A' && normalized[index] <= 'Z') {
                normalized[index] = (char) (normalized[index] + ('a' - 'A'));
            }
        }
        byte[] encoded = null;
        byte[] digest = null;
        ByteBuffer bytes = null;
        try {
            bytes = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(normalized));
            encoded = new byte[bytes.remaining()];
            bytes.get(encoded);
            digest = sha256(encoded);
            int low = 0;
            int high = sortedDigests.length / DIGEST_LENGTH - 1;
            while (low <= high) {
                int middle = (low + high) >>> 1;
                int comparison = compare(sortedDigests, middle * DIGEST_LENGTH, digest, 0);
                if (comparison == 0) return true;
                if (comparison < 0) low = middle + 1;
                else high = middle - 1;
            }
            return false;
        } catch (CharacterCodingException impossibleUtf8Failure) {
            throw new IllegalStateException("password cannot be UTF-8 encoded", impossibleUtf8Failure);
        } finally {
            Arrays.fill(normalized, '\0');
            if (bytes != null && bytes.hasArray()) Arrays.fill(bytes.array(), (byte) 0);
            if (encoded != null) Arrays.fill(encoded, (byte) 0);
            if (digest != null) Arrays.fill(digest, (byte) 0);
        }
    }

    @Override
    public String datasetVersion() {
        return DATASET_VERSION;
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static int compare(byte[] left, int leftOffset, byte[] right, int rightOffset) {
        for (int index = 0; index < DIGEST_LENGTH; index++) {
            int comparison = Integer.compare(
                    Byte.toUnsignedInt(left[leftOffset + index]), Byte.toUnsignedInt(right[rightOffset + index]));
            if (comparison != 0) return comparison;
        }
        return 0;
    }
}
