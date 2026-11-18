package com.aimall.gateway;

import com.aimall.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 统一鉴权:
 * 1. 清除客户端自带的 X-User-Id / X-User-Name, 防止伪造;
 * 2. 白名单之外的 /api/** 必须携带有效 JWT;
 * 3. 校验通过后把用户信息通过请求头透传给下游服务。
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private final String secret;

    public AuthGlobalFilter(@Value("${mall.jwt.secret}") String secret) {
        this.secret = secret;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        ServerHttpRequest.Builder builder = request.mutate().headers(h -> {
            h.remove("X-User-Id");
            h.remove("X-User-Name");
        });

        if (isPublic(request.getMethod(), path)) {
            return chain.filter(exchange.mutate().request(builder.build()).build());
        }

        String auth = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            return unauthorized(exchange, "未登录或登录已过期");
        }
        try {
            Claims claims = JwtUtil.parse(secret, auth.substring(7).trim());
            String userId = claims.getSubject();
            String username = String.valueOf(claims.get("username"));
            builder.headers(h -> {
                h.set("X-User-Id", userId);
                h.set("X-User-Name", username);
            });
            return chain.filter(exchange.mutate().request(builder.build()).build());
        } catch (JwtException | IllegalArgumentException e) {
            return unauthorized(exchange, "令牌无效或已过期");
        }
    }

    private boolean isPublic(HttpMethod method, String path) {
        if (!path.startsWith("/api/")) {
            return true; // 静态页面等
        }
        if (path.equals("/api/users/login") || path.equals("/api/users/register")) {
            return true;
        }
        return HttpMethod.GET.equals(method) && path.startsWith("/api/products");
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":401,\"message\":\"" + message + "\",\"data\":null}";
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
