package com.monitorplatform.rule.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 规则查询DTO
 */
@Data
public class RuleQueryDTO {
    
    private String ruleName;
    
    private String ruleType;
    
    private String status;
    
    private Integer pageNum = 1;
    
    private Integer pageSize = 10;
}
