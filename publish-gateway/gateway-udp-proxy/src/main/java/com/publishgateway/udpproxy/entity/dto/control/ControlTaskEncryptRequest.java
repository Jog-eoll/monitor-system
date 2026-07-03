package com.publishgateway.udpproxy.entity.dto.control;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 控制任务加密请求 —— 客户端提交明文控制任务 JSON，加密网关返回密文。
 */
@Data
public class ControlTaskEncryptRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 控制任务 ID */
    @NotBlank(message = "commandTaskId 不能为空")
    private String commandTaskId;

    /** 明文控制任务 JSON */
    @NotBlank(message = "plaintextControlTask 不能为空")
    private String plaintextControlTask;
}
