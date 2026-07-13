package com.infopublish.client.service;

import com.infopublish.client.entity.dto.precheck.PrecheckRequest;
import com.infopublish.client.entity.dto.precheck.PrecheckResponse;

/**
 * 自研信发平台 V2 发布前审核服务。
 */
public interface PublishPrecheckV2Service {

    /**
     * 执行 V2 发布前审核。
     *
     * @param request V2 预检查请求，节目单应由调用方直接提供
     * @return 预检查结果
     */
    PrecheckResponse precheckV2(PrecheckRequest request);
}
