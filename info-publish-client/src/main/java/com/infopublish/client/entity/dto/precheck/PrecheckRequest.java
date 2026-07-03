package com.infopublish.client.entity.dto.precheck;

import com.infopublish.client.entity.dto.sigma.SigmaVerifyRequest;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.List;

/**
 * 发布前预检查请求体
 * <p>
 * 对应信发平台发布前预检查接口请求字段。
 * </p>
 */
@Data
public class PrecheckRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 本次发布请求 ID，需支持幂等 */
    @NotBlank(message = "requestId 不能为空")
    private String requestId;

    /** 信发平台接口根地址 */
    @NotBlank(message = "sigmaBaseUrl 不能为空")
    private String sigmaBaseUrl;

    /** 待播放列表 ID；为空时客户端调用信发平台获取 */
    private String playlistId;

    /** 目标设备信息；合并接口签发 publishPermit 时必填 */
    private SigmaVerifyRequest.TargetRef target;

    /** 待播放列表中的播放文件；合并接口签发 publishPermit 时必填 */
    private List<SigmaVerifyRequest.PlaylistItem> items;

    /** 操作人 ID */
    private String operatorId;

    /** 整体超时时间（毫秒），默认 300000（5分钟） */
    private Integer timeoutMs;

    /**
     * 获取有效超时时间
     */
    public int getEffectiveTimeoutMs() {
        return (timeoutMs != null && timeoutMs > 0) ? timeoutMs : 300000;
    }
}
