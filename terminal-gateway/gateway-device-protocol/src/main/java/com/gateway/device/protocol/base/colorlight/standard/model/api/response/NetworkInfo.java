package com.gateway.device.protocol.base.colorlight.standard.model.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.gateway.device.protocol.base.colorlight.standard.model.NetworkTypeEnum;
import com.gateway.device.protocol.common.serialize.BoolSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * GET /api/network.json 响应体，同时复用为 POST /api/network 请求体。
 *
 * <p>ColorLight 网络配置 API 采用"先 GET 获取完整配置 → 修改目标条目 → POST 回写"模式，
 * 因此请求体与响应体结构一致。本类作为唯一共享 POJO，同时用于 GET 反序列化和 POST 序列化。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetworkInfo {

    private List<NetworkType> types;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkType {
        private NetworkTypeEnum type;
        private String SSID;
        private String pass;
        @JsonSerialize(using = BoolSerializer.BoolToIntSerializer.class)
        @JsonDeserialize(using = BoolSerializer.IntToBoolDeserializer.class)
        private boolean enabled;
        @JsonSerialize(using = BoolSerializer.BoolToIntSerializer.class)
        @JsonDeserialize(using = BoolSerializer.IntToBoolDeserializer.class)
        private boolean isstatic;
        private Integer channel;
        private String mac;
        private String dns1;
        private String dns2;
        @JsonProperty("ips")
        private IpInfo ips;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IpInfo {
        private String ip;
        private String mask;
        private String gateway;
        private String broadcast;
    }
}
