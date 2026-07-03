package com.monitorplatform.rule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 敏感关键词实体
 */
@Data
@TableName("sensitive_keyword")
public class SensitiveKeyword {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String keyword;
    
    private String category;
    
    private String severity;
    
    private String status;
    
    private String remark;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
}
