package com.infopublish.client.entity.dto.precheck;

import lombok.Data;

import java.io.Serializable;

/**
 * 基础检查结果
 * <p>
 * 包含 UKey 认证、客户端认证、Sigma 进程绑定、网关链路四项检查。
 * </p>
 */
@Data
public class BasicChecks implements Serializable {

    private static final long serialVersionUID = 1L;

    /** UKey 是否认证 */
    private boolean ukeyAuthenticated;

    /** 客户端是否认证 */
    private boolean clientAuthenticated;

    /** Sigma 进程是否绑定 */
    private boolean sigmaProcessBound;

    /** 网关链路是否可用 */
    private boolean gatewayReady;

    /**
     * 四项检查是否全部通过
     */
    public boolean isAllPassed() {
        return ukeyAuthenticated && clientAuthenticated && sigmaProcessBound && gatewayReady;
    }

    /**
     * 全部标记为 false
     */
    public static BasicChecks allFalse() {
        BasicChecks checks = new BasicChecks();
        checks.setUkeyAuthenticated(false);
        checks.setClientAuthenticated(false);
        checks.setSigmaProcessBound(false);
        checks.setGatewayReady(false);
        return checks;
    }
}
