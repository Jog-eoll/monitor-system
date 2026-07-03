package com.gateway.udpproxy.snapshot;

import lombok.Builder;
import lombok.Data;

/**
 * 截图结果
 */
@Data
@Builder
public class SnapshotResult {
    private boolean success;
    private String localFilePath;
    private String message;
}
