package com.monitorplatform.rule.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitorplatform.common.entity.Result;
import com.monitorplatform.rule.entity.AlarmThreshold;
import com.monitorplatform.rule.service.AlarmThresholdService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 告警阈值控制器
 */
@RestController
@RequestMapping("/rule/threshold")
public class AlarmThresholdController {
    
    @Autowired
    private AlarmThresholdService thresholdService;
    
    /**
     * 分页查询阈值
     */
    @GetMapping("/page")
    public Result<Page<AlarmThreshold>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                              @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<AlarmThreshold> page = thresholdService.page(pageNum, pageSize);
        return Result.success(page);
    }
    
    /**
     * 新增阈值
     */
    @PostMapping("/add")
    public Result<String> add(@RequestBody @Valid AlarmThreshold threshold) {
        boolean success = thresholdService.add(threshold);
        return success ? Result.success("新增成功") : Result.error("新增失败");
    }
    
    /**
     * 修改阈值
     */
    @PostMapping("/update")
    public Result<String> update(@RequestBody @Valid AlarmThreshold threshold) {
        boolean success = thresholdService.update(threshold);
        return success ? Result.success("修改成功") : Result.error("修改失败");
    }
    
    /**
     * 删除阈值
     */
    @PostMapping("/delete/{id}")
    public Result<String> delete(@PathVariable Long id) {
        boolean success = thresholdService.delete(id);
        return success ? Result.success("删除成功") : Result.error("删除失败");
    }
    
    /**
     * 启用/禁用阈值
     */
    @PostMapping("/status/{id}/{status}")
    public Result<String> updateStatus(@PathVariable Long id, @PathVariable String status) {
        boolean success = thresholdService.updateStatus(id, status);
        return success ? Result.success("状态更新成功") : Result.error("状态更新失败");
    }
    
    /**
     * 获取所有阈值配置
     */
    @GetMapping("/list-all")
    public Result<List<AlarmThreshold>> listAll() {
        List<AlarmThreshold> list = thresholdService.listAll();
        return Result.success(list);
    }
}
