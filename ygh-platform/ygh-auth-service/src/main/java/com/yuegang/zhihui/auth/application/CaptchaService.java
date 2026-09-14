package com.yuegang.zhihui.auth.application;

import com.yuegang.zhihui.auth.api.dto.CaptchaResponse;
import com.yuegang.zhihui.auth.domain.CaptchaChallengeStore;
import com.yuegang.zhihui.auth.domain.SensitiveValueHasher;
import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import javax.imageio.ImageIO;

public final class CaptchaService {
    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final Duration TTL = Duration.ofMinutes(5);
    private final CaptchaChallengeStore store;
    private final SensitiveValueHasher hasher;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Supplier<String> answers;

    public CaptchaService(CaptchaChallengeStore store, SensitiveValueHasher hasher, Clock clock) {
        this(store, hasher, clock, null);
    }

    CaptchaService(CaptchaChallengeStore store, SensitiveValueHasher hasher, Clock clock,
            Supplier<String> answers) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.hasher = Objects.requireNonNull(hasher, "hasher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.answers = answers == null ? this::randomAnswer : answers;
    }

    public CaptchaResponse create() {
        String id = UUID.randomUUID().toString().replace("-", "");
        String answer = answers.get();
        store.save(id, hasher.hashCaptchaAnswer(answer), TTL);
        String base64 = Base64.getEncoder().encodeToString(renderPng(answer));
        return new CaptchaResponse(id, "image/png", base64,
                clock.instant().plus(TTL).atOffset(ZoneOffset.UTC));
    }

    public void verify(String challengeId, String answer) {
        String hash;
        try { hash = hasher.hashCaptchaAnswer(answer); }
        catch (IllegalArgumentException malformed) { throw new BusinessException(ErrorCode.VALIDATION_ERROR); }
        if (!store.consume(challengeId, hash)) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }

    private String randomAnswer() {
        char[] value = new char[6];
        for (int i = 0; i < value.length; i++) value[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        return new String(value);
    }

    private byte[] renderPng(String answer) {
        var image = new BufferedImage(180, 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(238, 242, 247));
            graphics.fillRect(0, 0, 180, 60);
            graphics.setStroke(new BasicStroke(1.2f));
            for (int line = 0; line < 8; line++) {
                graphics.setColor(new Color(random.nextInt(160), random.nextInt(160), random.nextInt(160), 110));
                graphics.drawLine(random.nextInt(180), random.nextInt(60), random.nextInt(180), random.nextInt(60));
            }
            graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 30));
            for (int index = 0; index < answer.length(); index++) {
                graphics.setColor(new Color(15 + random.nextInt(70), 35 + random.nextInt(70), 55 + random.nextInt(70)));
                graphics.rotate(Math.toRadians(random.nextInt(-12, 13)), 20 + index * 26, 35);
                graphics.drawString(String.valueOf(answer.charAt(index)), 16 + index * 26, 40 + random.nextInt(-4, 5));
                graphics.setTransform(new java.awt.geom.AffineTransform());
            }
            try (var output = new ByteArrayOutputStream()) {
                if (!ImageIO.write(image, "png", output)) throw new IllegalStateException("PNG encoder unavailable");
                return output.toByteArray();
            } catch (java.io.IOException impossible) {
                throw new IllegalStateException("captcha image cannot be encoded", impossible);
            }
        } finally { graphics.dispose(); }
    }
}
