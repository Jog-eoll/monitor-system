package com.gateway.device.protocol.base.colorlight.standard.model.api.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.gateway.device.protocol.common.serialize.BoolSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PUT /api/sync_program_mode 请求体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncProgramModePayload {

    private AudioSync audio;
    private GpsSync gps;
    private LanSync lan;
    private NtpSync ntp;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AudioSync {
        @JsonSerialize(using = BoolSerializer.BoolToIntSerializer.class)
        @JsonDeserialize(using = BoolSerializer.IntToBoolDeserializer.class)
        private Boolean enable;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GpsSync {
        @JsonSerialize(using = BoolSerializer.BoolToIntSerializer.class)
        @JsonDeserialize(using = BoolSerializer.IntToBoolDeserializer.class)
        private Boolean enable;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LanSync {
        @JsonSerialize(using = BoolSerializer.BoolToIntSerializer.class)
        @JsonDeserialize(using = BoolSerializer.IntToBoolDeserializer.class)
        private Boolean enable;
        private String role;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NtpSync {
        @JsonSerialize(using = BoolSerializer.BoolToIntSerializer.class)
        @JsonDeserialize(using = BoolSerializer.IntToBoolDeserializer.class)
        private Boolean enable;
        @JsonProperty("sync_ntp_server")
        private SyncNtpServer syncNtpServer;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncNtpServer {
        private Integer deviation;
        private String server;
        private Long interval;
        private Long threshold;
    }
}
