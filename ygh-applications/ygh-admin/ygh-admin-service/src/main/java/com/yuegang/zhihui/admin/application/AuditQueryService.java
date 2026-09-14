package com.yuegang.zhihui.admin.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuegang.zhihui.admin.api.AuditLogView;
import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import java.time.*;
import java.util.*;
import java.util.regex.*;
import org.springframework.web.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AuditQueryService {
    private static final Logger LOG = LoggerFactory.getLogger(AuditQueryService.class);
    private static final Pattern FIELD = Pattern.compile("([A-Za-z][A-Za-z0-9]*)=([^ ]+)");
    private final RestClient loki;
    private final ObjectMapper json;

    public AuditQueryService(String baseUrl, ObjectMapper json) {
        this.loki = RestClient.builder().baseUrl(baseUrl).build();
        this.json = json;
    }

    public List<AuditLogView> query(String user, String module, String action, String result,
                                    OffsetDateTime from, OffsetDateTime to, int limit) {
        OffsetDateTime end = to == null ? OffsetDateTime.now(ZoneOffset.UTC) : to;
        OffsetDateTime start = from == null ? end.minusDays(1) : from;
        if (!start.isBefore(end) || Duration.between(start, end).toDays() > 31) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        StringBuilder query = new StringBuilder("{job=~\".+\"} |= \"business_mutation\"");
        append(query, "userId", user); append(query, "method", action);
        try {
            String body = loki.get().uri(builder -> builder
                    .path("/loki/api/v1/query_range")
                    .queryParam("query", "{logql}")
                    .queryParam("start", start.toInstant().toEpochMilli() * 1_000_000L)
                    .queryParam("end", end.toInstant().toEpochMilli() * 1_000_000L)
                    .queryParam("limit", Math.max(1, Math.min(limit, 500)))
                    .queryParam("direction", "backward")
                    .build(Map.of("logql", query.toString()))).retrieve().body(String.class);
            JsonNode root = body == null || body.isBlank() ? null : json.readTree(body);
            List<AuditLogView> records = new ArrayList<>();
            if (root == null) return records;
            for (JsonNode stream : root.path("data").path("result")) {
                String service = stream.path("stream").path("job").asText("unknown");
                for (JsonNode entry : stream.path("values")) records.add(map(service, entry.get(0).asText(), entry.get(1).asText()));
            }
            records.sort(Comparator.comparing(AuditLogView::timestamp).reversed());
            return records.stream()
                    .filter(record -> module == null || module.isBlank() || record.module().contains(module))
                    .filter(record -> result == null || result.isBlank() || record.result().equalsIgnoreCase(result))
                    .limit(Math.max(1, Math.min(limit, 500))).toList();
        } catch (BusinessException failure) { throw failure; }
        catch (Exception failure) {
            LOG.warn("Loki audit query failed", failure);
            return List.of();
        }
    }

    private static AuditLogView map(String service, String nanos, String line) {
        Map<String, String> fields = new HashMap<>();
        Matcher matcher = FIELD.matcher(line); while (matcher.find()) fields.put(matcher.group(1), matcher.group(2));
        int status = parse(fields.get("status"));
        Instant instant = Instant.ofEpochSecond(Long.parseLong(nanos.substring(0, Math.max(1, nanos.length() - 9))), Long.parseLong(nanos.substring(Math.max(0, nanos.length() - 9))));
        return new AuditLogView(instant.atOffset(ZoneOffset.UTC), fields.get("userId"), service,
                fields.getOrDefault("method", "UNKNOWN") + " " + fields.getOrDefault("path", ""),
                status >= 200 && status < 400 ? "SUCCESS" : "FAILURE", status, fields.get("traceId"), line);
    }

    private static void append(StringBuilder query, String field, String value) { if (value != null && !value.isBlank()) query.append(" |= \"").append(field).append("=").append(safe(value)).append("\""); }
    private static String safe(String value) { if (!value.matches("[A-Za-z0-9_./:-]{1,128}")) throw new BusinessException(ErrorCode.VALIDATION_ERROR); return value; }
    private static int parse(String value) { try { return Integer.parseInt(value); } catch (Exception ignored) { return 0; } }
}
