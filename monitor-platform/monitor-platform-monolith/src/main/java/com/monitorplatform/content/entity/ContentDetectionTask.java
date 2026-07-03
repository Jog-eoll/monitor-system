package com.monitorplatform.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 内容检测任务实体
 */
@Data
@TableName("content_detection_task")
public class ContentDetectionTask {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String businessId;
    
    private String deviceId;
    
    private String deviceName;
    
    private String screenshotBase64;
    
    private String taskStatus;
    
    private Integer retryCount;
    
    private Integer maxRetry;
    
    private Integer priority;
    
    private LocalDateTime submitTime;
    
    private LocalDateTime startTime;
    
    private LocalDateTime completeTime;
    
    private String errorMessage;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
}
