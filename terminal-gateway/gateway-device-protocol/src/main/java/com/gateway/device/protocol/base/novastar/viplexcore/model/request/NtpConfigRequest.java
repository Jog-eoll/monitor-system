package com.gateway.device.protocol.base.novastar.viplexcore.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * NTP 配置请求 —— {@code {"sn":"...","TimingInfo":{"source":{...},"taskArray":[...]}}}。
 *
 * <p>对应 SDK {@code nvSetNetTimingInfoAsync}。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NtpConfigRequest {

    /**
     * 设备序列号
     */
    private String sn;

    /**
     * 授时信息（协议字段首字母大写）
     */
    @JsonProperty("TimingInfo")
    private TimingInfo timingInfo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimingInfo {

        /**
         * 授时来源
         */
        private Source source;

        /**
         * 授时任务列表
         */
        private List<Task> taskArray;
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
        private int type = 1;

        /**
         * 平台标识
         */
        @Builder.Default
        private int platform = 1;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Task {

        /**
         * 任务类型
         */
        @Builder.Default
        private String type = "NTP_CONFIG";

        /**
         * 操作类型
         */
        @Builder.Default
        private int action = 4;

        /**
         * NTP 配置数据
         */
        private NtpData data;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NtpData {

        /**
         * 是否启用 NTP
         */
        @Builder.Default
        private boolean enable = true;

        /**
         * NTP 服务器地址
         */
        private String server;
    }
}
