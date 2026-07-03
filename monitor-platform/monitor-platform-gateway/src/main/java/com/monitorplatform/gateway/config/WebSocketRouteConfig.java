package com.monitorplatform.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * WebSocket 专用路由，统一转发前端 STOMP 连接。
 */
@Configuration
public class WebSocketRouteConfig {

    @Bean
    public RouteLocator webSocketRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("monitor-websocket-stomp", route -> route
                        .path("/ws-endpoint/**", "/ws-endpoint")
                        .uri("lb:ws://monitor-websocket"))
                .build();
    }
}
