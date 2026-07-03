package com.monitorplatform.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket STOMP 配置类
 * 使用 STOMP 协议提供消息代理功能
 * 
 * 端点说明：
 * - 连接端点：/ws-endpoint
 * - 订阅前缀：/topic (用于广播消息)
 * - 发送前缀：/app (用于发送到@MessageMapping方法)
 * @author zqr
 */
@Configuration
@ConditionalOnProperty(name = "monitor.websocket.enabled", havingValue = "true")
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 客户端将连接到此 endpoint
        registry.addEndpoint("/ws-endpoint")
                .setAllowedOriginPatterns("*");
//                .withSockJS(); // 兼容不支持 WebSocket 的环境
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 定义消息代理前缀：客户端订阅 /topic 开头的主题
        registry.enableSimpleBroker("/topic");
        // 应用目的地前缀：@MessageMapping 注解的方法路径前缀
        registry.setApplicationDestinationPrefixes("/app");
    }
}
