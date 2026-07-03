package com.infopublish.client.service;

import com.infopublish.client.entity.dto.precheck.PrecheckRequest;
import com.infopublish.client.entity.dto.precheck.PrecheckResponse;

/**
 * 发布前预检查编排服务
 * <p>
 * 串联四项基础检查 + Sigma API 调用 + 文件校验的完整 precheck 流程。
 * 对应 Sigma 对接文档 Section 5 总体流程。
 * </p>
 */
public interface PublishPrecheckService {

    /**
     * 执行完整的发布前预检查
     *
     * @param request 预检查请求
     * @return 预检查结果
     */
    PrecheckResponse precheck(PrecheckRequest request);
}
