package com.monitorplatform.role.entity.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.monitorplatform.common.entity.PageDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * @Description 系统日志分页
 * @Author: zqr
 * @Date: 2023/3/14 17:06:39
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class PageSysLogDTO extends PageDTO {


    /**
     * 日志类型 系统：system 操作：operation
     */
    private String type;

    /**
     * 操作类型
     */
    private String actType;

    /**
     * 开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /**
     * 执行动作
     */
    private String keyword;

}
