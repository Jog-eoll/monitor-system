package com.gateway.device.protocol.base.novastar.viplexcore.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 屏体电源控制请求 —— {@code {"sn":"...","taskInfo":{"state":"..."}}}。
 *
 * <p>对应 SDK {@code nvSetScreenPowerStateAsync}。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenPowerRequest {

    /**
     * 设备序列号
     */
    private String sn;

    /**
     * 电源任务信息
     */
    private TaskInfo taskInfo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskInfo {

        /**
         * 电源状态: "ON" / "OFF"
         */
        private String state;
    }
}
