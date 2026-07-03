package com.infopublish.client.entity.dto.sigma;

import lombok.Data;

import java.io.Serializable;

/**
 * 发送前校验响应
 * <p>
 * 包含 publishPermit (HS256 JWT)，加密网关和后续链路凭此令牌确认客户端已批准本次发布。
 * </p>
 */
@Data
public class SigmaVerifyResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否通过校验 */
    private boolean success;

    /** 校验结果标识: PASS / FAILED */
    private String result;

    /** publishPermit JWT（三段式 header.payload.signature） */
    private String publishPermit;

    /** 播放列表摘要（SHA-256 hex） */
    private String playlistDigest;

    /** 请求 ID */
    private String requestId;

    /** precheckId */
    private String precheckId;

    /** 失败原因（success=false 时填充） */
    private String message;

    // ── 静态工厂 ──

    public static SigmaVerifyResponse pass(String publishPermit, String playlistDigest,
                                           String requestId, String precheckId) {
        SigmaVerifyResponse r = new SigmaVerifyResponse();
        r.success = true;
        r.result = "PASS";
        r.publishPermit = publishPermit;
        r.playlistDigest = playlistDigest;
        r.requestId = requestId;
        r.precheckId = precheckId;
        return r;
    }

    public static SigmaVerifyResponse fail(String message, String requestId) {
        SigmaVerifyResponse r = new SigmaVerifyResponse();
        r.success = false;
        r.result = "FAILED";
        r.message = message;
        r.requestId = requestId;
        return r;
    }
}
