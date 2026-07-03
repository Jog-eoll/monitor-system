package com.gateway.udpproxy.snapshot;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 截图策略注册表
 */
@Component
public class ScreenSnapshotStrategyRegistry {

    private final Map<String, ScreenSnapshotStrategy> strategyMap = new HashMap<>();
    private final ScreenSnapshotStrategy defaultStrategy;

    public ScreenSnapshotStrategyRegistry(List<ScreenSnapshotStrategy> strategies,
                                          NoopScreenSnapshotStrategy noopStrategy) {
        this.defaultStrategy = noopStrategy;
        for (ScreenSnapshotStrategy strategy : strategies) {
            String key = normalize(strategy.supportedManufacturer());
            strategyMap.put(key, strategy);
        }
    }

    public ScreenSnapshotStrategy resolve(String manufacturer) {
        return strategyMap.getOrDefault(normalize(manufacturer), defaultStrategy);
    }

    private String normalize(String manufacturer) {
        if (!StringUtils.hasText(manufacturer)) {
            return "unknown";
        }
        return manufacturer.trim().toLowerCase();
    }
}
