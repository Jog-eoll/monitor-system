package com.monitorplatform.rule.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 敏感关键词查询DTO
 */
@Data
public class KeywordQueryDTO {
    
    private String keyword;
    
    private String category;
    
    private String severity;
    
    private String status;
    
    private Integer pageNum = 1;
    
    private Integer pageSize = 10;
}
