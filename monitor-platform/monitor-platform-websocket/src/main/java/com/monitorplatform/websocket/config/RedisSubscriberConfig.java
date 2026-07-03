package com.monitorplatform.websocket.config;

import com.monitorplatform.common.websocket.WebSocketPushConstants;
import com.monitorplatform.websocket.listener.WebSocketPushMessageListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import javax.annotation.Resource;

/**
 * Redis 订阅配置，接收各业务服务发布的推送事件。
 */
@Configuration
@ConditionalOnProperty(name = "monitor.websocket.enabled", havingValue = "true")
public class RedisSubscriberConfig {

    @Resource
    private WebSocketPushMessageListener webSocketPushMessageListener;

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(webSocketPushMessageListener, new ChannelTopic(WebSocketPushConstants.REDIS_CHANNEL));
        return container;
    }
}
