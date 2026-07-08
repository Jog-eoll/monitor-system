package com.gateway.device.protocol.base.novastar.viplexcore.model.request;

import com.gateway.device.protocol.common.constant.ProtocolConstant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 以太网配置请求 —— {@code {"sn":"...","taskInfo":{"ethernets":[{...}]}}}。
 *
 * <p>对应 SDK {@code nvSetEthernetInfoAsync}。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EthernetInfoRequest {

    /**
     * 设备序列号
     */
    private String sn;

    /**
     * 以太网任务信息
     */
    private TaskInfo taskInfo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskInfo {

        /**
         * 以太网配置列表
         */
        private List<Ethernet> ethernets;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Ethernet {

        /**
         * 作用域 ID
         */
        @Builder.Default
        private int scopeId = -1;

        /**
         * 网口名称
         */
        @Builder.Default
        private String name = ProtocolConstant.DEFAULT_NETWORK_INTERFACE;

        /**
         * 是否 DHCP（与 useStaticIp 互逆）
         */
        private boolean dhcp;

        /**
         * IP 地址（静态模式）
         */
        private String ip;

        /**
         * 子网掩码（静态模式）
         */
        private String mask;

        /**
         * 网关（协议字段 gateWay，大写 W）
         */
        private String gateWay;

        /**
         * DNS 服务器列表
         */
        private List<String> dns;
    }
}
