package com.gateway.device.core.controller.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 密文发布请求 —— 加密网关通过 HTTP POST 投递到解密网关。
 * <p>
 * 加密网关将 StandardizedPublishPackage 加密后作为二进制 body 发送，
 * 同时在请求头中携带 deliveryTaskId 和 requestId。
 * </p>
 *
 * @see com.gateway.device.core.controller.SecureCommandController
 */
@Data
public class SecureCommandRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 请求 ID（用于幂等和日志追踪）
     */
    private String requestId;

    /**
     * 投递任务 ID（加密网关生成）
     */
    private String deliveryTaskId;

    /**
     * 加密后的密文字节（由 Controller 从 HTTP body 直接读取，不走 JSON 反序列化）
     */
    private transient byte[] encryptedPayload;
}
