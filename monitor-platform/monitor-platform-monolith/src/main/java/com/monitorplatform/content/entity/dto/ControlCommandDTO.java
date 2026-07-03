package com.monitorplatform.content.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 控制指令DTO
 * 管控平台发送给加密网关的控制指令
 */
@Data
public class ControlCommandDTO {

    /** 内容ID */
    @NotBlank(message = "内容ID不能为空")
    private String contentId;

    /** 指令：STOP_DISPLAY-停止显示，RESUME_DISPLAY-恢复显示 */
    @NotBlank(message = "指令不能为空")
    private String action;

    /** 操作人 */
    private String operator;

    /** 备注 */
    private String remark;
}
