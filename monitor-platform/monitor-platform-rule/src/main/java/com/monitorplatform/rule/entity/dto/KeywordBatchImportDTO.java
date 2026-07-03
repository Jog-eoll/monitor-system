package com.monitorplatform.rule.entity.dto;

import lombok.Data;

/**
 * 批量导入关键词DTO
 */
@Data
public class KeywordBatchImportDTO {
    
    private String category;
    
    private String severity;
    
    private String keywords;  // 多个关键词用逗号分隔
}
