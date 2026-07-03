package com.monitorplatform.rule.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitorplatform.rule.entity.SensitiveKeyword;
import com.monitorplatform.rule.entity.dto.KeywordBatchImportDTO;
import com.monitorplatform.rule.entity.dto.KeywordQueryDTO;
import com.monitorplatform.rule.mapper.SensitiveKeywordMapper;
import com.monitorplatform.rule.service.SensitiveKeywordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 敏感关键词服务实现
 */
@Service
public class SensitiveKeywordServiceImpl implements SensitiveKeywordService {
    
    @Autowired
    private SensitiveKeywordMapper keywordMapper;
    
    @Override
    public Page<SensitiveKeyword> page(KeywordQueryDTO dto) {
        Page<SensitiveKeyword> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        
        LambdaQueryWrapper<SensitiveKeyword> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(dto.getKeyword()), SensitiveKeyword::getKeyword, dto.getKeyword())
                .eq(StrUtil.isNotBlank(dto.getCategory()), SensitiveKeyword::getCategory, dto.getCategory())
                .eq(StrUtil.isNotBlank(dto.getSeverity()), SensitiveKeyword::getSeverity, dto.getSeverity())
                .eq(StrUtil.isNotBlank(dto.getStatus()), SensitiveKeyword::getStatus, dto.getStatus())
                .orderByDesc(SensitiveKeyword::getCreateTime);
        
        return keywordMapper.selectPage(page, wrapper);
    }
    
    @Override
    public boolean add(SensitiveKeyword keyword) {
        return keywordMapper.insert(keyword) > 0;
    }
    
    @Override
    public boolean update(SensitiveKeyword keyword) {
        return keywordMapper.updateById(keyword) > 0;
    }
    
    @Override
    public boolean delete(Long id) {
        return keywordMapper.deleteById(id) > 0;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteBatch(List<Long> ids) {
        return keywordMapper.deleteBatchIds(ids) > 0;
    }
    
    @Override
    public boolean updateStatus(Long id, String status) {
        SensitiveKeyword keyword = new SensitiveKeyword();
        keyword.setId(id);
        keyword.setStatus(status);
        return keywordMapper.updateById(keyword) > 0;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchImport(KeywordBatchImportDTO dto) {
        if (StrUtil.isBlank(dto.getKeywords())) {
            return false;
        }
        
        String[] keywords = dto.getKeywords().split("[,，\\n]");
        List<SensitiveKeyword> list = new ArrayList<>();
        
        for (String kw : keywords) {
            if (StrUtil.isNotBlank(kw)) {
                SensitiveKeyword keyword = new SensitiveKeyword();
                keyword.setKeyword(kw.trim());
                keyword.setCategory(dto.getCategory());
                keyword.setSeverity(dto.getSeverity());
                keyword.setStatus("enabled");
                list.add(keyword);
            }
        }
        
        for (SensitiveKeyword keyword : list) {
            keywordMapper.insert(keyword);
        }
        
        return true;
    }
}
