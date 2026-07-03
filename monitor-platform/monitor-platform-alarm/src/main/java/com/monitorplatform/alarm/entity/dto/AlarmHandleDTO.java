package com.monitorplatform.alarm.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 处理告警DTO
 */
@Data
public class AlarmHandleDTO {
    
    @NotNull(message = "告警ID不能为空")
    private Long alarmId;
    
    @NotBlank(message = "处理人不能为空")
    private String handleOperator;
    
    private String handleRemark;



}
