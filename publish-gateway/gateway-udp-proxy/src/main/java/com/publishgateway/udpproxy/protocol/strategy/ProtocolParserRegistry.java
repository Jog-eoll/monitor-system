package com.publishgateway.udpproxy.protocol.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 协议解析策略注册中心
 * 自动发现并注册所有 ProtocolParserStrategy 实现，根据厂家标识路由到对应策略
 */
@Slf4j
@Component
public class ProtocolParserRegistry {

    private final Map<String, ProtocolParserStrategy> strategyMap = new ConcurrentHashMap<>();
    private ProtocolParserStrategy defaultStrategy;

    /**
     * 自动注册所有 ProtocolParserStrategy 实现
     */
    @Autowired
    public ProtocolParserRegistry(List<ProtocolParserStrategy> strategies) {
        for (ProtocolParserStrategy s : strategies) {
            String key = s.supportedManufacturer().toLowerCase();
            strategyMap.put(key, s);
            log.info("【策略注册】注册协议解析策略: manufacturer={}, class={}", key, s.getClass().getSimpleName());
        }
        defaultStrategy = strategyMap.get("sigma");
        if (defaultStrategy != null) {
            log.info("【策略注册】默认策略: sigma -> {}", defaultStrategy.getClass().getSimpleName());
        } else {
            log.warn("【策略注册】未找到 sigma 默认策略，请确保 SigmaProtocolParser 已注册");
        }
    }

    /**
     * 根据厂家标识获取对应的解析策略
     *
     * @param manufacturer 厂家标识，为空时返回默认策略（sigma）
     * @return 对应的解析策略，未匹配时返回默认策略
     */
    public ProtocolParserStrategy getStrategy(String manufacturer) {
        if (manufacturer == null || manufacturer.isEmpty()) {
            return defaultStrategy;
        }
        return strategyMap.getOrDefault(manufacturer.toLowerCase(), defaultStrategy);
    }
}
