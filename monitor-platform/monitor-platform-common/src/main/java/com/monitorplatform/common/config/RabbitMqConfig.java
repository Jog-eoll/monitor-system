package com.monitorplatform.common.config;

public class RabbitMqConfig {

    public interface RabbitMqConstants {
        String ALARM_EXCHANGE = "monitor.alarm.exchange";
        String ALARM_QUEUE    = "monitor.alarm.queue";
        String ALARM_ROUTING  = "monitor.alarm.routingKey";
        // 可按需扩展其他队列
    }
}
