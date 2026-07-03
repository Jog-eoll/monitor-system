package com.publishgateway.udpproxy.entity.dto.control;

import lombok.Data;

import java.io.Serializable;

/**
 * 控制任务加密响应 —— 返回加密后的控制任务包及其 ID。
 */
@Data
public class ControlTaskEncryptResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 加密包 ID */
    private String encryptedCommandPackageId;

    /** 加密后的控制任务包（Base64 编码） */
    private String encryptedCommandPackage;

    /** 创建时间（epoch millis） */
    private long createdAt;

    public static ControlTaskEncryptResponse success(String packageId, byte[] encryptedBytes) {
        ControlTaskEncryptResponse resp = new ControlTaskEncryptResponse();
        resp.encryptedCommandPackageId = packageId;
        resp.encryptedCommandPackage = java.util.Base64.getEncoder().encodeToString(encryptedBytes);
        resp.createdAt = System.currentTimeMillis();
        return resp;
    }
}
