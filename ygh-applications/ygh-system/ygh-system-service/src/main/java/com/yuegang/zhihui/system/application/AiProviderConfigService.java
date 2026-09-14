package com.yuegang.zhihui.system.application;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.system.api.AiProviderConfigView;
import com.yuegang.zhihui.system.api.InternalAiProviderConfig;
import com.yuegang.zhihui.system.api.UpdateAiProviderConfigRequest;
import com.yuegang.zhihui.system.security.SystemSecretCipher;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

public final class AiProviderConfigService {
    private static final String SELECT = """
            SELECT provider,base_url,chat_model,embedding_model,web_search_enabled,api_key_ciphertext,api_key_nonce,
                   version,updated_at
            FROM system_ai_provider_config WHERE config_id=1
            """;
    private final JdbcTemplate jdbc;
    private final SystemSecretCipher secrets;

    public AiProviderConfigService(DataSource dataSource, SystemSecretCipher secrets) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.secrets = secrets;
    }

    public AiProviderConfigView view() {
        return jdbc.queryForObject(SELECT, (row, index) -> toView(
                row.getString(1), row.getString(2), row.getString(3), row.getString(4),
                row.getBoolean(5), row.getString(6), row.getLong(8), row.getTimestamp(9)));
    }

    public InternalAiProviderConfig internal() {
        return jdbc.queryForObject(SELECT, (row, index) -> new InternalAiProviderConfig(
                row.getString(1), row.getString(2), row.getString(3), row.getString(4),
                row.getBoolean(5), secrets.decrypt(row.getString(6), row.getString(7)), row.getLong(8)));
    }

    public AiProviderConfigView update(UpdateAiProviderConfigRequest request, long operator) {
        validateBaseUrl(request.baseUrl());
        var current = jdbc.queryForMap(SELECT);
        String oldCiphertext = (String) current.get("api_key_ciphertext");
        String ciphertext = oldCiphertext;
        String nonce = (String) current.get("api_key_nonce");
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            String normalized = request.apiKey().trim();
            if (normalized.length() < 16) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            var encrypted = secrets.encrypt(normalized);
            ciphertext = encrypted.ciphertext();
            nonce = encrypted.nonce();
        }
        int updated = jdbc.update("""
                UPDATE system_ai_provider_config
                   SET provider=?,base_url=?,chat_model=?,embedding_model=?,web_search_enabled=?,api_key_ciphertext=?,api_key_nonce=?,
                       updated_by=?,version=version+1
                 WHERE config_id=1 AND version=?
                """, request.provider(), request.baseUrl().trim(), request.chatModel().trim(),
                request.embeddingModel().trim(), request.webSearchEnabled(), ciphertext, nonce, operator,
                request.version());
        if (updated != 1) throw new BusinessException(ErrorCode.BUSINESS_CONFLICT);
        String oldDigest = digest(String.join("|", current.get("provider").toString(),
                current.get("base_url").toString(), current.get("chat_model").toString(),
                current.get("embedding_model").toString(), current.get("web_search_enabled").toString(),
                oldCiphertext == null ? "" : oldCiphertext));
        String newDigest = digest(String.join("|", request.provider(), request.baseUrl(), request.chatModel(),
                request.embeddingModel(), Boolean.toString(request.webSearchEnabled()),
                ciphertext == null ? "" : ciphertext));
        jdbc.update("""
                INSERT INTO system_configuration_audit(
                    id,config_type,config_key,old_digest,new_digest,operator_user_id
                ) VALUES(?,?,?,?,?,?)
                """, nextId(), "AI_PROVIDER", "primary", oldDigest, newDigest, operator);
        return view();
    }

    private static AiProviderConfigView toView(String provider, String baseUrl, String chatModel,
                                               String embeddingModel, boolean webSearchEnabled,
                                               String ciphertext, long version,
                                               Timestamp updatedAt) {
        boolean configured = ciphertext != null && !ciphertext.isBlank();
        OffsetDateTime changed = updatedAt == null ? null : updatedAt.toInstant().atOffset(ZoneOffset.UTC);
        return new AiProviderConfigView(provider, baseUrl, chatModel, embeddingModel, webSearchEnabled, configured,
                configured ? "••••••••" : "未配置", version, changed);
    }

    private static void validateBaseUrl(String value) {
        try {
            URI uri = URI.create(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException failure) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static long nextId() {
        return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    }
}
