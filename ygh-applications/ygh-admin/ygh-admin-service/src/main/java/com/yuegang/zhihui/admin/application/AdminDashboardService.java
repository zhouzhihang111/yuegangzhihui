package com.yuegang.zhihui.admin.application;

import com.yuegang.zhihui.admin.api.AdminDashboardView;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

public final class AdminDashboardService {
    private static final Duration CONNECT_TIMEOUT = Duration.ofMillis(800);
    private static final Duration READ_TIMEOUT = Duration.ofMillis(1_200);

    private final Map<String, String> urls;
    private final RestClient client;

    public AdminDashboardService(String encoded) {
        var configured = new LinkedHashMap<String, String>();
        for (String item : encoded.split(",")) {
            int separator = item.indexOf('=');
            if (separator > 0) {
                configured.put(item.substring(0, separator), item.substring(separator + 1));
            }
        }
        urls = Collections.unmodifiableMap(configured);

        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        client = RestClient.builder().requestFactory(requestFactory).build();
    }

    public AdminDashboardView dashboard() {
        List<AdminDashboardView.ServiceStatus> statuses = probeConcurrently();
        long healthy = statuses.stream().filter(status -> "UP".equals(status.status())).count();
        var pending = statuses.stream()
                .filter(status -> !"UP".equals(status.status()))
                .map(status -> new AdminDashboardView.PendingMetric(
                        "SERVICE_UNAVAILABLE", 1, status.service()))
                .toList();
        return new AdminDashboardView(
                new AdminDashboardView.Summary(statuses.size(), healthy, statuses.size() - healthy),
                statuses,
                pending,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    private List<AdminDashboardView.ServiceStatus> probeConcurrently() {
        if (urls.isEmpty()) {
            return List.of();
        }

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var probes = new ArrayList<Future<AdminDashboardView.ServiceStatus>>(urls.size());
            urls.forEach((name, url) -> probes.add(executor.submit(() -> probe(name, url))));
            return probes.stream().map(AdminDashboardService::completed).toList();
        }
    }

    private AdminDashboardView.ServiceStatus probe(String name, String url) {
        long startedAt = System.nanoTime();
        String status = "DOWN";
        try {
            Map<?, ?> response = client.get().uri(url).retrieve().body(Map.class);
            if (response != null) {
                status = Objects.toString(response.get("status"), "UNKNOWN");
            }
        } catch (RuntimeException ignored) {
            status = "DOWN";
        }
        return new AdminDashboardView.ServiceStatus(
                name, status, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private static AdminDashboardView.ServiceStatus completed(
            Future<AdminDashboardView.ServiceStatus> probe) {
        try {
            return probe.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("service health aggregation interrupted", interrupted);
        } catch (java.util.concurrent.ExecutionException failure) {
            throw new IllegalStateException("service health aggregation failed", failure.getCause());
        }
    }
}
