package com.gateway.device.protocol.api;

import com.gateway.device.protocol.model.CommandResult;

/**
 * 厂商响应 → 统一结果 映射。
 *
 * @param <R> 厂商响应对象类型
 */
public interface ResultMapper<R> {

    /**
     * 将厂商响应映射为统一命令结果
     */
    CommandResult map(R vendorResponse);
}
