package com.aimall.gateway;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 统一路由: /api/xxx/** -> lb://服务名 /xxx/**  (StripPrefix=1)。
 * 服务内部的 /internal/** 接口不对外暴露 (没有对应路由)。
 */
@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator mallRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("user-service", r -> r.path("/api/users/**")
                        .filters(f -> f.stripPrefix(1)).uri("lb://user-service"))
                .route("product-service", r -> r.path("/api/products/**")
                        .filters(f -> f.stripPrefix(1)).uri("lb://product-service"))
                .route("trade-cart", r -> r.path("/api/cart/**")
                        .filters(f -> f.stripPrefix(1)).uri("lb://trade-service"))
                .route("trade-orders", r -> r.path("/api/orders/**")
                        .filters(f -> f.stripPrefix(1)).uri("lb://trade-service"))
                .route("ai-service", r -> r.path("/api/ai/**")
                        .filters(f -> f.stripPrefix(1)).uri("lb://ai-service"))
                .build();
    }
}
