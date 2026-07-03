package com.monitorplatform.rule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警阈值配置实体
 */
@Data
@TableName("alarm_threshold")
public class AlarmThreshold {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String thresholdName;
    
    private String thresholdType;
    
    private String thresholdConfig;
    
    private String alertLevel;
    
    private String status;
    
    private String description;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
}
