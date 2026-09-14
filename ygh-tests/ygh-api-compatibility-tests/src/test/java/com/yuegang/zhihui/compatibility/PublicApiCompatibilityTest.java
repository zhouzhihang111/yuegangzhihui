package com.yuegang.zhihui.compatibility;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.mq.DeadLetterRecord;
import com.yuegang.zhihui.common.mybatis.AuditableEntity;
import com.yuegang.zhihui.common.redis.RedisKeyBuilder;
import com.yuegang.zhihui.common.security.CurrentUserPrincipal;
import com.yuegang.zhihui.common.test.MutableTestClock;
import com.yuegang.zhihui.common.web.GlobalExceptionHandler;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

public class PublicApiCompatibilityTest {

    private static final int EXPECTED_SIGNATURE_COUNT = 577;
    private static final String EXPECTED_SHA256 =
            "147ef7fade1c5acbe77b4442beaf879adf0eb32bece5f5d77b4315949e200d67";
    private static final List<Class<?>> MODULE_ANCHORS = List.of(
            ApiResponse.class,
            GlobalExceptionHandler.class,
            CurrentUserPrincipal.class,
            AuditableEntity.class,
            RedisKeyBuilder.class,
            DeadLetterRecord.class,
            MutableTestClock.class);

    @Test
    void publicCommonApiMatchesReviewedBaseline() throws Exception {
        Set<String> signatures = collectSignatures();
        String joined = String.join("\n", signatures);
        String hash = sha256(joined);
        Path currentReport = Path.of("target", "public-api-current.txt");
        Files.createDirectories(currentReport.getParent());
        Files.writeString(currentReport, joined + '\n', StandardCharsets.UTF_8);

        Set<String> baseline = readBaseline();
        assertThat(baseline)
                .withFailMessage("Reviewed API baseline integrity failure")
                .hasSize(EXPECTED_SIGNATURE_COUNT);
        assertThat(sha256(String.join("\n", baseline))).isEqualTo(EXPECTED_SHA256);

        var removed = new TreeSet<>(baseline);
        removed.removeAll(signatures);
        var added = new TreeSet<>(signatures);
        added.removeAll(baseline);

        assertThat(removed.isEmpty() && added.isEmpty())
                .withFailMessage("Public API baseline changed. currentCount=%s currentSha256=%s"
                                + "%nREMOVED:%n%s%nADDED:%n%s",
                        signatures.size(), hash,
                        String.join("\n", removed), String.join("\n", added))
                .isTrue();
    }

    @Test
    void protectedNestedTypesAndMembersArePartOfTheSignatureModel() {
        var signatures = new TreeSet<String>();
        appendTypeSignatures(ProtectedApiFixture.class, signatures);

        assertThat(signatures).anyMatch(signature ->
                signature.startsWith("TYPE protected static class "));
        assertThat(signatures).anyMatch(signature ->
                signature.contains("protected void") && signature.contains("protectedOperation"));
    }

    private static Set<String> collectSignatures()
            throws IOException, URISyntaxException, ClassNotFoundException {
        var classNames = new TreeSet<String>();
        for (Class<?> anchor : MODULE_ANCHORS) {
            Path root = Path.of(anchor.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isDirectory(root)) {
                try (var paths = Files.walk(root)) {
                    paths.filter(path -> path.toString().endsWith(".class"))
                            .map(path -> root.relativize(path).toString())
                            .map(PublicApiCompatibilityTest::toClassName)
                            .filter(name -> name.startsWith("com.yuegang.zhihui.common."))
                            .forEach(classNames::add);
                }
            } else {
                try (var jar = new JarFile(root.toFile())) {
                    jar.stream()
                            .filter(entry -> !entry.isDirectory() && entry.getName().endsWith(".class"))
                            .map(entry -> toClassName(entry.getName()))
                            .filter(name -> name.startsWith("com.yuegang.zhihui.common."))
                            .forEach(classNames::add);
                }
            }
        }

        var signatures = new TreeSet<String>();
        for (String className : classNames) {
            Class<?> type = Class.forName(className, false,
                    PublicApiCompatibilityTest.class.getClassLoader());
            appendTypeSignatures(type, signatures);
        }
        return signatures;
    }

    private static void appendTypeSignatures(Class<?> type, Set<String> signatures) {
        int typeModifiers = type.getModifiers();
        if ((!Modifier.isPublic(typeModifiers) && !Modifier.isProtected(typeModifiers))
                || type.isSynthetic()) {
            return;
        }
        signatures.add("TYPE " + type.toGenericString());
        Arrays.stream(type.getDeclaredConstructors())
                .filter(PublicApiCompatibilityTest::isPublicApi)
                .filter(constructor -> !constructor.isSynthetic())
                .map(constructor -> "CONSTRUCTOR " + constructor.toGenericString())
                .forEach(signatures::add);
        Arrays.stream(type.getDeclaredMethods())
                .filter(PublicApiCompatibilityTest::isPublicApi)
                .filter(method -> !method.isSynthetic() && !method.isBridge())
                .map(method -> "METHOD " + method.toGenericString())
                .forEach(signatures::add);
        Arrays.stream(type.getDeclaredFields())
                .filter(PublicApiCompatibilityTest::isPublicApi)
                .filter(field -> !field.isSynthetic())
                .map(field -> "FIELD " + field.toGenericString())
                .forEach(signatures::add);
    }

    private static Set<String> readBaseline() throws IOException {
        try (InputStream stream = PublicApiCompatibilityTest.class
                .getResourceAsStream("/public-api-baseline.txt")) {
            if (stream == null) {
                throw new IOException("public-api-baseline.txt is missing; see target/public-api-current.txt");
            }
            return new TreeSet<>(new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .lines().filter(line -> !line.isBlank()).toList());
        }
    }

    private static String toClassName(String resourceName) {
        return resourceName.substring(0, resourceName.length() - 6)
                .replace('/', '.').replace('\\', '.');
    }

    private static boolean isPublicApi(Member member) {
        int modifiers = member.getModifiers();
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    private static String sha256(String value) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    protected static class ProtectedApiFixture {
        protected void protectedOperation() {
        }
    }
}
