package com.infopublish.client.service;

import com.infopublish.client.entity.dto.sigma.SigmaPublishStatusResponse;
import com.infopublish.client.entity.dto.sigma.SigmaVerifyRequest;
import com.infopublish.client.entity.dto.sigma.SigmaVerifyResponse;

/**
 * 信发平台安全发布服务
 */
public interface SigmaPublishService {

    /**
     * 获取客户端发布就绪状态
     */
    SigmaPublishStatusResponse getStatus();

    /**
     * 签发发布许可：生成 playlistDigest + 签发 publishPermit
     */
    SigmaVerifyResponse verify(SigmaVerifyRequest request);

    /**
     * 基于已通过的 precheck 请求签发发布许可
     */
    SigmaVerifyResponse issuePermit(SigmaVerifyRequest request);
}
