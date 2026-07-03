package com.gateway.udpproxy.snapshot;

import com.gateway.udpproxy.entity.UdpProxyRule;
import org.springframework.stereotype.Component;

/**
 * 默认空策略
 */
@Component
public class NoopScreenSnapshotStrategy implements ScreenSnapshotStrategy {

    @Override
    public String supportedManufacturer() {
        return "unknown";
    }

    @Override
    public SnapshotResult capture(UdpProxyRule rule, String outputFilePath, int timeoutMs) {
        return SnapshotResult.builder()
                .success(false)
                .message("no snapshot strategy for manufacturer=" + rule.getManufacturer())
                .build();
    }
}
