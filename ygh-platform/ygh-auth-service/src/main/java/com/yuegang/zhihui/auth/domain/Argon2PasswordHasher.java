package com.yuegang.zhihui.auth.domain;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

public final class Argon2PasswordHasher {
    public static final String ALGORITHM = "ARGON2ID";
    public static final int VERSION = 1;
    private static final int ARGON2_VERSION_13 = 0x13;
    private static final int MAX_ENCODED_LENGTH = 256;
    private static final int MAX_ACCEPTED_MEMORY_KIB = 32 * 1024;
    private static final int MAX_ACCEPTED_ITERATIONS = 4;
    private static final int MAX_ACCEPTED_PARALLELISM = 2;
    private static final Pattern ARGON2_ENCODING = Pattern.compile(
            "^\\$argon2id\\$v=19\\$m=([1-9][0-9]{0,5}),t=([1-9][0-9]?),p=([1-9][0-9]?)"
                    + "\\$([A-Za-z0-9+/]{22})={0,2}\\$([A-Za-z0-9+/]{43})={0,2}$");

    private final int saltLength;
    private final int hashLength;
    private final int parallelism;
    private final int memoryKiB;
    private final int iterations;
    private final SecureRandom secureRandom;
    private final Semaphore capacity;
    private final Duration capacityWait;

    public Argon2PasswordHasher(int saltLength, int hashLength, int parallelism, int memoryKiB, int iterations) {
        this(saltLength, hashLength, parallelism, memoryKiB, iterations, 2, Duration.ofSeconds(5));
    }

    public Argon2PasswordHasher(
            int saltLength, int hashLength, int parallelism, int memoryKiB, int iterations,
            int maximumConcurrentOperations, Duration capacityWait) {
        if (saltLength != 16 || hashLength != 32 || parallelism < 1
                || parallelism > MAX_ACCEPTED_PARALLELISM
                || memoryKiB < 8 || memoryKiB > MAX_ACCEPTED_MEMORY_KIB
                || iterations < 1 || iterations > MAX_ACCEPTED_ITERATIONS) {
            throw new IllegalArgumentException("invalid Argon2id parameters");
        }
        if (maximumConcurrentOperations < 1) {
            throw new IllegalArgumentException("maximumConcurrentOperations must be positive");
        }
        this.capacityWait = Objects.requireNonNull(capacityWait, "capacityWait must not be null");
        if (capacityWait.isNegative() || capacityWait.isZero()) {
            throw new IllegalArgumentException("capacityWait must be positive");
        }
        this.saltLength = saltLength;
        this.hashLength = hashLength;
        this.parallelism = parallelism;
        this.memoryKiB = memoryKiB;
        this.iterations = iterations;
        this.secureRandom = new SecureRandom();
        this.capacity = new Semaphore(maximumConcurrentOperations, true);
    }

    public static Argon2PasswordHasher owaspMinimum() {
        return new Argon2PasswordHasher(16, 32, 1, 19 * 1024, 2);
    }

    public PasswordDigest hash(char[] rawPassword) {
        Objects.requireNonNull(rawPassword, "rawPassword must not be null");
        return withCapacity(() -> {
            byte[] salt = new byte[saltLength];
            byte[] hash = new byte[hashLength];
            secureRandom.nextBytes(salt);
            try {
                generate(rawPassword, salt, hash, memoryKiB, iterations, parallelism);
                String encoded = "$argon2id$v=19$m=" + memoryKiB + ",t=" + iterations + ",p=" + parallelism
                        + "$" + Base64.getEncoder().withoutPadding().encodeToString(salt)
                        + "$" + Base64.getEncoder().withoutPadding().encodeToString(hash);
                return new PasswordDigest(encoded, ALGORITHM, VERSION);
            } finally {
                Arrays.fill(salt, (byte) 0);
                Arrays.fill(hash, (byte) 0);
            }
        });
    }

    public boolean matches(char[] rawPassword, PasswordDigest digest) {
        ParsedEncoding parsed = parse(digest);
        if (rawPassword == null || parsed == null) {
            return false;
        }
        return withCapacity(() -> {
            byte[] actual = new byte[parsed.hash().length];
            try {
                generate(rawPassword, parsed.salt(), actual,
                        parsed.memoryKiB(), parsed.iterations(), parsed.parallelism());
                return MessageDigest.isEqual(actual, parsed.hash());
            } finally {
                Arrays.fill(actual, (byte) 0);
                parsed.clear();
            }
        });
    }

    public boolean needsUpgrade(PasswordDigest digest) {
        ParsedEncoding parsed = parse(digest);
        if (parsed == null) {
            return true;
        }
        try {
            return parsed.memoryKiB() != memoryKiB || parsed.iterations() != iterations
                    || parsed.parallelism() != parallelism || parsed.salt().length != saltLength
                    || parsed.hash().length != hashLength;
        } finally {
            parsed.clear();
        }
    }

    private ParsedEncoding parse(PasswordDigest digest) {
        if (digest == null || !ALGORITHM.equals(digest.algorithm().toUpperCase(Locale.ROOT))
                || digest.version() != VERSION || digest.hash() == null
                || digest.hash().length() > MAX_ENCODED_LENGTH) {
            return null;
        }
        Matcher matcher = ARGON2_ENCODING.matcher(digest.hash());
        if (!matcher.matches()) {
            return null;
        }
        try {
            int memory = Integer.parseInt(matcher.group(1));
            int time = Integer.parseInt(matcher.group(2));
            int lanes = Integer.parseInt(matcher.group(3));
            if (memory < 8 || memory > MAX_ACCEPTED_MEMORY_KIB || time > MAX_ACCEPTED_ITERATIONS
                    || lanes > MAX_ACCEPTED_PARALLELISM) {
                return null;
            }
            byte[] salt = Base64.getDecoder().decode(matcher.group(4));
            byte[] hash = Base64.getDecoder().decode(matcher.group(5));
            if (salt.length != 16 || hash.length != 32) {
                Arrays.fill(salt, (byte) 0);
                Arrays.fill(hash, (byte) 0);
                return null;
            }
            return new ParsedEncoding(memory, time, lanes, salt, hash);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private void generate(char[] password, byte[] salt, byte[] output, int memory, int time, int lanes) {
        byte[] encodedPassword = encodeUtf8(password);
        Argon2Parameters parameters = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(ARGON2_VERSION_13).withSalt(salt).withMemoryAsKB(memory)
                .withIterations(time).withParallelism(lanes).build();
        try {
            var generator = new Argon2BytesGenerator();
            generator.init(parameters);
            generator.generateBytes(encodedPassword, output);
        } finally {
            parameters.clear();
            Arrays.fill(encodedPassword, (byte) 0);
        }
    }

    private byte[] encodeUtf8(char[] password) {
        ByteBuffer buffer = null;
        try {
            buffer = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(password));
            byte[] encoded = new byte[buffer.remaining()];
            buffer.get(encoded);
            return encoded;
        } catch (CharacterCodingException invalidUnicode) {
            throw new IllegalArgumentException("password contains invalid Unicode", invalidUnicode);
        } finally {
            if (buffer != null && buffer.hasArray()) {
                Arrays.fill(buffer.array(), (byte) 0);
            }
        }
    }

    private <T> T withCapacity(java.util.function.Supplier<T> operation) {
        boolean acquired;
        try {
            acquired = capacity.tryAcquire(capacityWait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new PasswordHashCapacityException("password hashing interrupted", interrupted);
        }
        if (!acquired) {
            throw new PasswordHashCapacityException("password hashing capacity exhausted");
        }
        try {
            return operation.get();
        } finally {
            capacity.release();
        }
    }

    private record ParsedEncoding(int memoryKiB, int iterations, int parallelism, byte[] salt, byte[] hash) {
        private void clear() {
            Arrays.fill(salt, (byte) 0);
            Arrays.fill(hash, (byte) 0);
        }
    }
}
