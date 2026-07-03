package com.infopublish.client.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志实体
 */
@Data
@TableName("operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 事件类型 */
    private String eventType;

    /** 事件详情 */
    private String detail;

    /** 状态：SUCCESS/FAIL */
    private String status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
