package com.infopublish.client.entity.dto.publish;

import com.infopublish.client.entity.dto.sigma.SigmaVerifyRequest;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 内容发布执行请求。
 */
@Data
public class ContentPublishRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "requestId 不能为空")
    private String requestId;

    @NotBlank(message = "sigmaBaseUrl 不能为空")
    private String sigmaBaseUrl;

    private String operatorId;

    @Valid
    private SigmaVerifyRequest.TargetRef target;

    private Options options;

    private Integer timeoutMs;

    public int getEffectiveTimeoutMs() {
        return timeoutMs != null && timeoutMs > 0 ? timeoutMs : 300000;
    }

    @Data
    public static class Options implements Serializable {
        private static final long serialVersionUID = 1L;

        private Boolean clearBeforePublish;
        private Boolean checkExistence;
        private Boolean waitForDelivery;
    }
}
