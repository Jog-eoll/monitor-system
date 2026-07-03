package com.monitorplatform.log.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.monitorplatform.common.entity.PageDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class LinkLogQueryDTO extends PageDTO {

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime endTime;

    private Long chainId;
    private String chainCode;
    private String sourceIp;
    private String boardIp;
    private String eventLevel;
    private String resultStatus;
    private String keyword;
}
