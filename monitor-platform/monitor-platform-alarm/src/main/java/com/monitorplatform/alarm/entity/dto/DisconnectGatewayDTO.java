package com.monitorplatform.alarm.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 应急黑屏处置请求DTO
 */
@Data
public class DisconnectGatewayDTO {

    /**
     * 情报板 IP（必填）
     * 系统会根据 infoBoardIp 自动查询对应的终端网关 IP
     * 终端网关与情报板是一对一关系，情报板 IP 唯一确定终端网关
     */
    @NotBlank(message = "情报板 IP 不能为空")
    private String infoBoardIp;

    /**
     * 情报板端口（可选，默认 9520）
     */
    private Integer infoBoardPort;

    /**
     * 告警 ID（可选）
     * 如果提供，黑屏成功后会自动将告警状态更新为已处置
     */
    private Long alarmId;

    /**
     * 当前被切断的内容 ID（可选）。
     * 提供后仅当该 contentId 存在待处理告警时，才清理大屏上的对应文件。
     */
    private String contentId;

    /**
     * 链路 ID（可选，仅作参考记录，不用于查询）
     */
    private String chainId;

    /**
     * 操作原因
     */
    private String reason;

    /**
     * 操作人
     */
    private String operator;
}
