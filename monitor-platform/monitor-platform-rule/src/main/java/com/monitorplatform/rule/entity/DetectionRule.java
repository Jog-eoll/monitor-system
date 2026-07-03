package com.monitorplatform.rule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 检测规则实体
 */
@Data
@TableName("detection_rule")
public class DetectionRule {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String ruleName;
    
    private String ruleType;
    
    private String ruleConfig;
    
    private Integer priority;
    
    private String status;
    
    private String description;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
}
