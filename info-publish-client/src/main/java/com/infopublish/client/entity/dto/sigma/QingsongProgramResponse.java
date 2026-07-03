package com.infopublish.client.entity.dto.sigma;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 青松按情报板 IP 回调获取节目单响应。
 */
@Data
public class QingsongProgramResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer code;

    private String message;

    private ProgramData data;

    public boolean isSuccessful() {
        return code != null && code == 0 && data != null;
    }

    @Data
    public static class ProgramData implements Serializable {
        private static final long serialVersionUID = 1L;

        private Boolean success;

        private String playlistId;

        private SigmaVerifyRequest.TargetRef target;

        private List<SigmaVerifyRequest.PlaylistItem> items;
    }
}
