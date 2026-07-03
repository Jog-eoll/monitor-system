package com.monitorplatform.rule.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitorplatform.common.entity.Result;
import com.monitorplatform.rule.entity.DetectionRule;
import com.monitorplatform.rule.entity.dto.RuleQueryDTO;
import com.monitorplatform.rule.service.DetectionRuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 检测规则控制器
 */
@RestController
@RequestMapping("/rule/detection")
public class DetectionRuleController {
    
    @Autowired
    private DetectionRuleService ruleService;
    
    /**
     * 分页查询规则
     */
    @PostMapping("/page")
    public Result<Page<DetectionRule>> page(@RequestBody RuleQueryDTO dto) {
        Page<DetectionRule> page = ruleService.page(dto);
        return Result.success(page);
    }
    
    /**
     * 新增规则
     */
    @PostMapping("/add")
    public Result<String> add(@RequestBody @Valid DetectionRule rule) {
        boolean success = ruleService.add(rule);
        return success ? Result.success("新增成功") : Result.error("新增失败");
    }
    
    /**
     * 修改规则
     */
    @PostMapping("/update")
    public Result<String> update(@RequestBody @Valid DetectionRule rule) {
        boolean success = ruleService.update(rule);
        return success ? Result.success("修改成功") : Result.error("修改失败");
    }
    
    /**
     * 删除规则
     */
    @PostMapping("/delete/{id}")
    public Result<String> delete(@PathVariable Long id) {
        boolean success = ruleService.delete(id);
        return success ? Result.success("删除成功") : Result.error("删除失败");
    }
    
    /**
     * 启用/禁用规则
     */
    @PostMapping("/status/{id}/{status}")
    public Result<String> updateStatus(@PathVariable Long id, @PathVariable String status) {
        boolean success = ruleService.updateStatus(id, status);
        return success ? Result.success("状态更新成功") : Result.error("状态更新失败");
    }
    
    /**
     * 获取所有启用的规则
     */
    @GetMapping("/list-enabled")
    public Result<List<DetectionRule>> listEnabled() {
        List<DetectionRule> list = ruleService.listEnabled();
        return Result.success(list);
    }
}
