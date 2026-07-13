package com.infopublish.client.entity.dto.publish;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class ContentPublishV2BaseRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String requestId;

    private String operatorId;

    private TargetRef target;

    private String command;

    private Map<String, Object> params;

    private Integer timeoutMs;

    @Data
    public static class TargetRef implements Serializable {
        private static final long serialVersionUID = 1L;

        private String ip;

        private Object port;

        private String deviceId;

        private String vendorHint;
    }
}
