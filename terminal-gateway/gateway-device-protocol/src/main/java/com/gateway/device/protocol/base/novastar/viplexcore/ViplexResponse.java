package com.gateway.device.protocol.base.novastar.viplexcore;

import com.fasterxml.jackson.databind.JsonNode;
import com.gateway.device.protocol.common.JsonCustomMapper;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

/**
 * ViplexCore SDK 通用响应模型 —— 封装 SDK 异步回调的 code + data。
 *
 * <p>与 transport 层的 JNA 回调解耦，protocol 层仅依赖此 POJO。</p>
 */
@Getter
public class ViplexResponse {

    private final int code;
    private final String data;
    private final boolean success;
    private final boolean timeout;

    public ViplexResponse(int code, String data) {
        this.code = code;
        this.data = data;
        this.success = code == 0;
        this.timeout = false;
    }

    private ViplexResponse(boolean timeout) {
        this.code = -1;
        this.data = null;
        this.success = false;
        this.timeout = timeout;
    }

    public static ViplexResponse failure(int code, String msg) {
        return new ViplexResponse(code, msg);
    }

    public static ViplexResponse timeout() {
        return new ViplexResponse(true);
    }

    /**
     * 将 data (JSON) 解析为指定类型
     */
    public <T> T parseData(Class<T> clazz) {
        if (StringUtils.isEmpty(data)) return null;
        try {
            return JsonCustomMapper.get().readValue(data, clazz);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 将 data 解析为 JsonNode
     */
    public JsonNode dataAsJson() {
        if (StringUtils.isEmpty(data)) return null;
        try {
            return JsonCustomMapper.get().readTree(data);
        } catch (Exception e) {
            return null;
        }
    }
}
