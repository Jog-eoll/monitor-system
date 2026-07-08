package com.gateway.device.protocol.base.novastar.viplexcore.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备重启请求 —— {@code {"sn":"...","taskInfo":{"type":"REBOOT","source":{...},"executionType":"IMMEDIATELY","reason":"gateway command"}}}。
 *
 * <p>对应 SDK {@code nvSetReBootTaskAsync}。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RebootRequest {

    /**
     * 设备序列号
     */
    private String sn;

    /**
     * 重启任务信息
     */
    private TaskInfo taskInfo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskInfo {

        /**
         * 任务类型
         */
        @Builder.Default
        private String type = "REBOOT";

        /**
         * 命令来源
         */
        private Source source;

        /**
         * 执行策略
         */
        @Builder.Default
        private String executionType = "IMMEDIATELY";

        /**
         * 重启原因
         */
        @Builder.Default
        private String reason = "gateway command";
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Source {

        /**
         * 来源类型
         */
        @Builder.Default
        private int type = 0;

        /**
         * 平台标识
         */
        @Builder.Default
        private int platform = 2;
    }
}
