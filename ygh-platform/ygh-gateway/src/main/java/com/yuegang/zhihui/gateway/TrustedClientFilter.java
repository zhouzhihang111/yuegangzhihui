package com.yuegang.zhihui.gateway;

import com.yuegang.zhihui.common.security.InternalRequestSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

/**
 * 将边缘节点观测到的真实 IP 签名放入 Header，替换掉所有不可信的伪造 IP 头。
 */
@Component
@ConditionalOnProperty(prefix = "ygh.internal-request", name = "enabled", havingValue = "true", matchIfMissing = true)
final class TrustedClientFilter implements GlobalFilter, Ordered {

    // 内部签名工具（修正字段名单数，统一调用）
    private final InternalRequestSignature signatureTool;
    // 系统时钟
    private final Clock clock;

    @Autowired
    TrustedClientFilter(@Value("${ygh.internal-request.hmac-base64}") String encodedSecret) {
        this(encodedSecret, Clock.systemUTC());
    }

    TrustedClientFilter(String encodedSecret, Clock clock) {
        this.clock = clock;
        byte[] secret;
        try {
            secret = Base64.getDecoder().decode(encodedSecret); // 解码 HMAC 密钥
        } catch (IllegalArgumentException malformed) {
            throw new IllegalStateException("YGH_INTERNAL_REQUEST_HMAC_BASE64 is malformed", malformed);
        }
        try {
            this.signatureTool = new InternalRequestSignature(secret, clock, Duration.ofSeconds(30));
        } finally {
            Arrays.fill(secret, (byte) 0); // 擦除密钥内存，安全加固
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return Mono.error(new IllegalStateException("client remote address is unavailable"));
        }
        String clientIp = stripScope(remote.getAddress().getHostAddress()); // 获取真实实体 IP
        String traceId = exchange.getRequest().getHeaders().getFirst(GatewayHeaders.TRACE_ID);
        String requestId = exchange.getRequest().getHeaders().getFirst(GatewayHeaders.REQUEST_ID);
        Instant timestamp = clock.instant();

        // 构造待签名的元数据包：包含 IP，追踪ID，请求方法和路径
        var metadata = new InternalRequestSignature.Metadata(
                clientIp, traceId, requestId, exchange.getRequest().getMethod().name(),
                exchange.getRequest().getPath().pathWithinApplication().value(), timestamp);
        // 调用签名工具，局部变量改名避免和成员重名
        String ipSignature = signatureTool.sign(metadata); // 生成 HMAC 签名

        var request = exchange.getRequest().mutate().headers((HttpHeaders headers) -> {
            headers.remove(GatewayHeaders.CLIENT_IP);
            headers.remove(GatewayHeaders.CLIENT_IP_TIMESTAMP);
            headers.remove(GatewayHeaders.CLIENT_IP_SIGNATURE);
            headers.set(GatewayHeaders.CLIENT_IP, clientIp);
            headers.set(GatewayHeaders.CLIENT_IP_TIMESTAMP, Long.toString(timestamp.toEpochMilli()));
            headers.set(GatewayHeaders.CLIENT_IP_SIGNATURE, ipSignature);
        }).build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    // 移到类顶层，解决“不允许修饰符、方法嵌套”报错
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 25;
    }

    // 独立工具方法，移出filter内部，可正常解析stripScope
    private static String stripScope(String address) {
        int scope = address.indexOf('%'); // 过滤 IPv6 的作用域标识符
        return scope < 0 ? address : address.substring(0, scope);
    }
}