package com.monitorplatform.rule.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitorplatform.common.entity.Result;
import com.monitorplatform.rule.entity.SensitiveKeyword;
import com.monitorplatform.rule.entity.dto.KeywordBatchImportDTO;
import com.monitorplatform.rule.entity.dto.KeywordQueryDTO;
import com.monitorplatform.rule.service.SensitiveKeywordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 敏感关键词控制器
 */
@RestController
@RequestMapping("/rule/keyword")
public class KeywordController {
    
    @Autowired
    private SensitiveKeywordService keywordService;
    
    /**
     * 分页查询关键词
     */
    @PostMapping("/page")
    public Result<Page<SensitiveKeyword>> page(@RequestBody KeywordQueryDTO dto) {
        Page<SensitiveKeyword> page = keywordService.page(dto);
        return Result.success(page);
    }
    
    /**
     * 新增关键词
     */
    @PostMapping("/add")
    public Result<String> add(@RequestBody @Valid SensitiveKeyword keyword) {
        boolean success = keywordService.add(keyword);
        return success ? Result.success("新增成功") : Result.error("新增失败");
    }
    
    /**
     * 修改关键词
     */
    @PostMapping("/update")
    public Result<String> update(@RequestBody @Valid SensitiveKeyword keyword) {
        boolean success = keywordService.update(keyword);
        return success ? Result.success("修改成功") : Result.error("修改失败");
    }
    
    /**
     * 删除关键词
     */
    @PostMapping("/delete/{id}")
    public Result<String> delete(@PathVariable Long id) {
        boolean success = keywordService.delete(id);
        return success ? Result.success("删除成功") : Result.error("删除失败");
    }
    
    /**
     * 批量删除关键词
     */
    @PostMapping("/delete-batch")
    public Result<String> deleteBatch(@RequestBody List<Long> ids) {
        boolean success = keywordService.deleteBatch(ids);
        return success ? Result.success("批量删除成功") : Result.error("批量删除失败");
    }
    
    /**
     * 启用/禁用关键词
     */
    @PostMapping("/status/{id}/{status}")
    public Result<String> updateStatus(@PathVariable Long id, @PathVariable String status) {
        boolean success = keywordService.updateStatus(id, status);
        return success ? Result.success("状态更新成功") : Result.error("状态更新失败");
    }
    
    /**
     * 批量导入关键词
     */
    @PostMapping("/batch-import")
    public Result<String> batchImport(@RequestBody KeywordBatchImportDTO dto) {
        boolean success = keywordService.batchImport(dto);
        return success ? Result.success("批量导入成功") : Result.error("批量导入失败");
    }
}
