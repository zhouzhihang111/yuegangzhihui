package com.yuegang.zhihui.auth.infrastructure;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.SecureDirectoryStream;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public final class RsaSigningKeyRing {
    private static final Pattern SAFE_KID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final int MAX_PUBLIC_KEYS = 8;
    private final RSAKey activeKey;
    private final List<JWK> publicKeys;

    private RsaSigningKeyRing(RSAKey activeKey, List<JWK> publicKeys) {
        this.activeKey = activeKey;
        this.publicKeys = List.copyOf(publicKeys);
    }

    public static RsaSigningKeyRing load(Path directory, String activeKid) {
        Objects.requireNonNull(directory, "directory must not be null");
        if (activeKid == null || !SAFE_KID.matcher(activeKid).matches()) {
            throw new IllegalArgumentException("active key id is unsafe");
        }
        Path root = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("JWT key directory is unavailable");
        }
        try {
            requireDirectoryPermissions(root);
            List<Path> publicFiles;
            try (var paths = Files.list(root)) {
                publicFiles = paths.filter(path -> path.getFileName().toString().endsWith(".public.pem"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
            }
            if (publicFiles.isEmpty() || publicFiles.size() > MAX_PUBLIC_KEYS) {
                throw new IllegalStateException("JWT public key count is outside allowed bounds");
            }
            var publicKeys = new ArrayList<JWK>();
            RSAKey activePublic = null;
            for (Path publicFile : publicFiles) {
                requireRegularChild(root, publicFile);
                String fileName = publicFile.getFileName().toString();
                String kid = fileName.substring(0, fileName.length() - ".public.pem".length());
                if (!SAFE_KID.matcher(kid).matches()) throw new IllegalStateException("JWT key id is unsafe");
                RSAPublicKey publicKey = readPublic(publicFile);
                requireStrength(publicKey);
                RSAKey jwk = new RSAKey.Builder(publicKey).keyID(kid).keyUse(KeyUse.SIGNATURE)
                        .algorithm(JWSAlgorithm.RS256).build();
                publicKeys.add(jwk);
                if (kid.equals(activeKid)) activePublic = jwk;
            }
            if (activePublic == null) throw new IllegalStateException("active JWT public key is missing");
            Path privateFile = root.resolve(activeKid + ".private.pem").normalize();
            requireRegularChild(root, privateFile);
            RSAPrivateKey privateKey = readPrivate(privateFile);
            if (!privateKey.getModulus().equals(activePublic.toRSAPublicKey().getModulus())) {
                throw new IllegalStateException("active JWT key pair does not match");
            }
            if (!(privateKey instanceof RSAPrivateCrtKey crtKey)
                    || !crtKey.getPublicExponent().equals(activePublic.toRSAPublicKey().getPublicExponent())) {
                throw new IllegalStateException("active JWT key pair exponent does not match");
            }
            verifyKeyPair(privateKey, activePublic.toRSAPublicKey());
            RSAKey active = new RSAKey.Builder(activePublic).privateKey(privateKey).build();
            return new RsaSigningKeyRing(active, publicKeys);
        } catch (IOException | java.security.GeneralSecurityException | JOSEException failure) {
            throw new IllegalStateException("JWT signing key ring cannot be loaded", failure);
        }
    }

    public RSAKey activeSigningKey() { return activeKey; }

    public Map<String, Object> publicJwkSet() { return new JWKSet(publicKeys).toJSONObject(); }

    private static void requireRegularChild(Path root, Path file) throws IOException {
        Path normalized = file.toAbsolutePath().normalize();
        if (!normalized.getParent().equals(root) || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(normalized)) {
            throw new IOException("JWT key file is not a regular direct child");
        }
        if (normalized.getFileName().toString().endsWith(".private.pem")) requirePrivatePermissions(normalized);
    }

    private static void requirePrivatePermissions(Path file) throws IOException {
        try {
            var permissions = Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS);
            var forbidden = java.util.EnumSet.of(
                    java.nio.file.attribute.PosixFilePermission.GROUP_READ,
                    java.nio.file.attribute.PosixFilePermission.GROUP_WRITE,
                    java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE,
                    java.nio.file.attribute.PosixFilePermission.OTHERS_READ,
                    java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE,
                    java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE);
            if (permissions.stream().anyMatch(forbidden::contains)) {
                throw new IOException("JWT private key permissions are too broad");
            }
        } catch (UnsupportedOperationException ignoredOnNonPosixFileSystem) {
            // Windows ACL governance is enforced by deployment; POSIX deployments fail closed here.
        }
    }

    private static void requireDirectoryPermissions(Path directory) throws IOException {
        try {
            var permissions = Files.getPosixFilePermissions(directory, LinkOption.NOFOLLOW_LINKS);
            if (permissions.contains(java.nio.file.attribute.PosixFilePermission.GROUP_WRITE)
                    || permissions.contains(java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE)) {
                throw new IOException("JWT key directory is writable by group or others");
            }
        } catch (UnsupportedOperationException ignoredOnNonPosixFileSystem) {
            // Windows ACL governance is enforced by deployment.
        }
    }

    private static void verifyKeyPair(RSAPrivateKey privateKey, RSAPublicKey publicKey)
            throws java.security.GeneralSecurityException {
        byte[] challenge = new byte[32];
        byte[] signatureBytes = null;
        new java.security.SecureRandom().nextBytes(challenge);
        try {
            var signer = java.security.Signature.getInstance("SHA256withRSA");
            signer.initSign(privateKey);
            signer.update(challenge);
            signatureBytes = signer.sign();
            var verifier = java.security.Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(challenge);
            if (!verifier.verify(signatureBytes)) throw new java.security.InvalidKeyException("JWT key pair verification failed");
        } finally {
            Arrays.fill(challenge, (byte) 0);
            if (signatureBytes != null) Arrays.fill(signatureBytes, (byte) 0);
        }
    }

    private static RSAPublicKey readPublic(Path path) throws IOException, java.security.GeneralSecurityException {
        byte[] encoded = decodePem(path, "PUBLIC KEY");
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
        } finally {
            Arrays.fill(encoded, (byte) 0);
        }
    }

    private static RSAPrivateKey readPrivate(Path path) throws IOException, java.security.GeneralSecurityException {
        byte[] encoded = decodePem(path, "PRIVATE KEY");
        try {
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } finally {
            Arrays.fill(encoded, (byte) 0);
        }
    }

    private static byte[] decodePem(Path path, String type) throws IOException {
        byte[] pem = readWithoutFollowingLinks(path);
        byte[] begin = ("-----BEGIN " + type + "-----").getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] end = ("-----END " + type + "-----").getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] body = null;
        try {
            int beginAt = indexOf(pem, begin, 0);
            int endAt = indexOf(pem, end, begin.length);
            if (beginAt != 0 || endAt < 0 || !onlyAsciiWhitespace(pem, endAt + end.length)) {
                throw new IOException("invalid PEM envelope");
            }
            body = Arrays.copyOfRange(pem, begin.length, endAt);
            if (!validBase64Body(body)) throw new IOException("invalid PEM body characters");
            try { return Base64.getMimeDecoder().decode(body); }
            catch (IllegalArgumentException malformed) { throw new IOException("invalid PEM body", malformed); }
        } finally {
            Arrays.fill(pem, (byte) 0);
            if (body != null) Arrays.fill(body, (byte) 0);
        }
    }

    private static byte[] readWithoutFollowingLinks(Path path) throws IOException {
        Path parent = path.getParent();
        try (var directory = Files.newDirectoryStream(parent)) {
            java.nio.channels.SeekableByteChannel channel;
            if (directory instanceof SecureDirectoryStream<Path> secureDirectory) {
                channel = secureDirectory.newByteChannel(
                        path.getFileName(), java.util.Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
            } else {
                if (Files.getFileAttributeView(parent,
                        java.nio.file.attribute.PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS) != null) {
                    throw new IOException("secure JWT key directory access is unavailable");
                }
                channel = Files.newByteChannel(path,
                        StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
            }
            try (channel) {
                if (channel.size() <= 0 || channel.size() > 32 * 1024) throw new IOException("JWT key file size is invalid");
                ByteBuffer buffer = ByteBuffer.allocate(Math.toIntExact(channel.size()));
                try {
                    while (buffer.hasRemaining() && channel.read(buffer) >= 0) { }
                    if (buffer.hasRemaining()) throw new IOException("JWT key file was truncated while reading");
                    return Arrays.copyOf(buffer.array(), buffer.position());
                } finally {
                    Arrays.fill(buffer.array(), (byte) 0);
                }
            }
        }
    }

    private static int indexOf(byte[] source, byte[] target, int from) {
        outer: for (int index = from; index <= source.length - target.length; index++) {
            for (int offset = 0; offset < target.length; offset++) {
                if (source[index + offset] != target[offset]) continue outer;
            }
            return index;
        }
        return -1;
    }

    private static boolean onlyAsciiWhitespace(byte[] source, int from) {
        for (int index = from; index < source.length; index++) {
            byte value = source[index];
            if (value != ' ' && value != '\r' && value != '\n' && value != '\t') return false;
        }
        return true;
    }

    private static boolean validBase64Body(byte[] body) {
        for (byte value : body) {
            boolean base64 = value >= 'A' && value <= 'Z' || value >= 'a' && value <= 'z'
                    || value >= '0' && value <= '9' || value == '+' || value == '/' || value == '=';
            boolean whitespace = value == ' ' || value == '\r' || value == '\n' || value == '\t';
            if (!base64 && !whitespace) return false;
        }
        return true;
    }

    private static void requireStrength(RSAPublicKey key) {
        if (key.getModulus().bitLength() < 2048) throw new IllegalStateException("JWT RSA key is weaker than 2048 bits");
    }
}
