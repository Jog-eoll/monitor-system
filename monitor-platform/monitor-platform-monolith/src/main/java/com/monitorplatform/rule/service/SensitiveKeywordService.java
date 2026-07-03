package com.monitorplatform.rule.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitorplatform.rule.entity.SensitiveKeyword;
import com.monitorplatform.rule.entity.dto.KeywordBatchImportDTO;
import com.monitorplatform.rule.entity.dto.KeywordQueryDTO;

import java.util.List;

/**
 * 敏感关键词服务
 */
public interface SensitiveKeywordService {
    
    /**
     * 分页查询关键词
     */
    Page<SensitiveKeyword> page(KeywordQueryDTO dto);
    
    /**
     * 新增关键词
     */
    boolean add(SensitiveKeyword keyword);
    
    /**
     * 修改关键词
     */
    boolean update(SensitiveKeyword keyword);
    
    /**
     * 删除关键词
     */
    boolean delete(Long id);
    
    /**
     * 批量删除
     */
    boolean deleteBatch(List<Long> ids);
    
    /**
     * 启用/禁用关键词
     */
    boolean updateStatus(Long id, String status);
    
    /**
     * 批量导入关键词
     */
    boolean batchImport(KeywordBatchImportDTO dto);
}
