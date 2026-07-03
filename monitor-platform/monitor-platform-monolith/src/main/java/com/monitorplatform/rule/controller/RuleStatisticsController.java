package com.monitorplatform.rule.controller;

import com.monitorplatform.common.entity.Result;
import com.monitorplatform.rule.entity.dto.RuleStatisticsDTO;
import com.monitorplatform.rule.service.RuleStatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 规则统计控制器
 */
@RestController
@RequestMapping("/rule/statistics")
public class RuleStatisticsController {
    
    @Autowired
    private RuleStatisticsService statisticsService;
    
    /**
     * 获取规则配置统计数据
     */
    @GetMapping("/overview")
    public Result<RuleStatisticsDTO> getStatistics() {
        RuleStatisticsDTO statistics = statisticsService.getStatistics();
        return Result.success(statistics);
    }
}
