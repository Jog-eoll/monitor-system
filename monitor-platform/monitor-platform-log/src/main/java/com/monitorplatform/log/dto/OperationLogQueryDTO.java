package com.monitorplatform.log.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.monitorplatform.common.entity.PageDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class OperationLogQueryDTO extends PageDTO {

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime endTime;

    private String operatorName;
    private String clientIp;
    private String actModule;
    private String actType;
    private String keyword;
}
