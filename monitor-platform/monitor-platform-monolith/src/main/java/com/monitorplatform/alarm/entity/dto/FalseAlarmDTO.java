package com.monitorplatform.alarm.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 标记误报DTO
 */
@Data
public class FalseAlarmDTO {

    @NotNull(message = "告警ID不能为空")
    private Long alarmId;

    @NotBlank(message = "操作人不能为空")
    private String operator;

    /** 误报原因 */
    private String reason;
}
