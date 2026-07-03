package com.infopublish.client.entity.dto.sigma;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 客户端状态响应
 * <p>
 * 对应 GET /api/client/status，返回客户端当前运行状态摘要。
 * Sigma 或管控平台可据此判断客户端是否具备发布条件。
 * </p>
 */
@Data
public class SigmaPublishStatusResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** UKey 是否已认证 */
    private boolean ukeyAuthenticated;

    /** 客户端是否已通过平台认证 */
    private boolean clientAuthenticated;

    /** Sigma 进程是否已绑定 */
    private boolean sigmaProcessBound;

    /** 加密网关是否就绪 */
    private boolean gatewayReady;

    /** 安全发布模块是否启用 */
    private boolean securePublishEnabled;

    /** 内容审计是否通过（若启用） */
    private boolean contentAuditPassed;

    /** 当前客户端版本号 */
    private String clientVersion;

    /** 当前绑定进程信息 */
    private String boundProcessInfo;

    /** 附加状态（扩展字段） */
    private Map<String, Object> extras;

    /**
     * 判断是否具备发布条件
     */
    public boolean isReadyForPublish() {
        return ukeyAuthenticated && clientAuthenticated && sigmaProcessBound && gatewayReady;
    }
}
