package com.infopublish.client.entity.dto.precheck;

import com.infopublish.client.entity.dto.sigma.SigmaVerifyRequest;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 发布前预检查响应体
 * <p>
 * 对应信发平台发布前预检查接口响应字段。
 * </p>
 */
@Data
public class PrecheckResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 接口是否执行成功 */
    private boolean success;

    /** 是否允许信发平台继续发布 */
    private boolean publishAllowed;

    /** 兼容字段：是否允许信发平台继续发布，与 publishAllowed 保持一致 */
    private boolean allowed;

    /** 结果: PASS 或 FAILED */
    private String result;

    /** 待播放列表 ID */
    private String playlistId;

    /** 基础检查结果 */
    private BasicChecks checks;

    /** 发送前校验结果 */
    private FileCheckResult checkResult;

    /** 说明 */
    private String message;

    /** 请求 ID（幂等回传） */
    private String requestId;

    /** 预检查 ID，v1 复用 requestId，供 verify 阶段回传关联 */
    private String precheckId;

    /** publishPermit JWT（三段式 header.payload.signature） */
    private String publishPermit;

    /** 播放列表摘要（SHA-256 hex） */
    private String playlistDigest;

    /** 发布许可签发结果说明 */
    private String permitMessage;

    private String infoBoardIp;

    private Integer infoBoardPort;

    /** 青松回调返回的目标情报板信息 */
    private SigmaVerifyRequest.TargetRef target;

    /** 青松回调返回的播放内容列表 */
    private List<SigmaVerifyRequest.PlaylistItem> items;

    // ========== 静态工厂方法 ==========

    /**
     * 基础检查失败
     */
    public static PrecheckResponse basicCheckFailed(String playlistId, BasicChecks checks,
                                                     String message, String requestId) {
        PrecheckResponse resp = new PrecheckResponse();
        resp.setSuccess(true);
        resp.setPublishAllowed(false);
        resp.setAllowed(false);
        resp.setResult("FAILED");
        resp.setPlaylistId(playlistId);
        resp.setChecks(checks);
        resp.setCheckResult(FileCheckResult.empty());
        resp.setMessage(message);
        resp.setRequestId(requestId);
        resp.setPrecheckId(requestId);
        return resp;
    }

    /**
     * 文件校验失败
     */
    public static PrecheckResponse fileCheckFailed(String playlistId, BasicChecks checks,
                                                    FileCheckResult checkResult,
                                                    String message, String requestId) {
        PrecheckResponse resp = new PrecheckResponse();
        resp.setSuccess(true);
        resp.setPublishAllowed(false);
        resp.setAllowed(false);
        resp.setResult("FAILED");
        resp.setPlaylistId(playlistId);
        resp.setChecks(checks);
        resp.setCheckResult(checkResult);
        resp.setMessage(message);
        resp.setRequestId(requestId);
        resp.setPrecheckId(requestId);
        return resp;
    }

    /**
     * 全部通过
     */
    public static PrecheckResponse passed(String playlistId, BasicChecks checks,
                                           FileCheckResult checkResult, String requestId) {
        PrecheckResponse resp = new PrecheckResponse();
        resp.setSuccess(true);
        resp.setPublishAllowed(true);
        resp.setAllowed(true);
        resp.setResult("PASS");
        resp.setPlaylistId(playlistId);
        resp.setChecks(checks);
        resp.setCheckResult(checkResult);
        resp.setMessage("发送前检查通过，允许发布");
        resp.setRequestId(requestId);
        resp.setPrecheckId(requestId);
        return resp;
    }

    /**
     * 补充发布许可信息
     */
    public void attachPermit(String publishPermit, String playlistDigest) {
        this.publishPermit = publishPermit;
        this.playlistDigest = playlistDigest;
        this.permitMessage = "发布许可已签发";
    }

    /**
     * 补充发布许可签发失败说明。
     * <p>
     * 预检查已经通过但发布许可签发失败时，不能继续发布。
     * </p>
     */
    public void attachPermitFailure(String message) {
        this.success = true;
        this.publishAllowed = false;
        this.allowed = false;
        this.result = "FAILED";
        this.permitMessage = message;
        this.message = message;
    }

    public void attachInfoBoard(String ip, Integer port) {
        this.infoBoardIp = ip;
        this.infoBoardPort = port;
    }

    public void attachProgram(SigmaVerifyRequest.TargetRef target,
                              List<SigmaVerifyRequest.PlaylistItem> items) {
        this.target = target;
        this.items = items;
    }

    /**
     * 接口执行异常
     */
    public static PrecheckResponse error(String message, String requestId) {
        PrecheckResponse resp = new PrecheckResponse();
        resp.setSuccess(false);
        resp.setPublishAllowed(false);
        resp.setAllowed(false);
        resp.setResult("FAILED");
        resp.setChecks(BasicChecks.allFalse());
        resp.setCheckResult(FileCheckResult.empty());
        resp.setMessage(message);
        resp.setRequestId(requestId);
        resp.setPrecheckId(requestId);
        return resp;
    }
}
