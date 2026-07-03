package com.monitorplatform.content.feign;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitorplatform.content.entity.vo.CommonServiceResponseVO;
import com.monitorplatform.rule.entity.DetectionRule;
import com.monitorplatform.rule.entity.SensitiveKeyword;
import com.monitorplatform.rule.entity.dto.KeywordQueryDTO;
import com.monitorplatform.rule.service.DetectionRuleService;
import com.monitorplatform.rule.service.SensitiveKeywordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * Rule 服务本地调用（替代原 Feign 客户端）
 */
@Slf4j
@Service
public class RuleFeignClient {

    @Resource
    private DetectionRuleService detectionRuleService;

    @Resource
    private SensitiveKeywordService sensitiveKeywordService;

    public CommonServiceResponseVO<List<DetectionRuleVO>> listEnabledDetectionRules() {
        CommonServiceResponseVO<List<DetectionRuleVO>> resp = new CommonServiceResponseVO<>();
        try {
            List<DetectionRule> rules = detectionRuleService.listEnabled();
            List<DetectionRuleVO> voList = new ArrayList<>();
            if (rules != null) {
                for (DetectionRule r : rules) {
                    DetectionRuleVO vo = new DetectionRuleVO();
                    vo.id = r.getId();
                    vo.ruleName = r.getRuleName();
                    vo.ruleType = r.getRuleType();
                    vo.ruleConfig = r.getRuleConfig();
                    vo.priority = r.getPriority();
                    voList.add(vo);
                }
            }
            resp.setCode(200);
            resp.setData(voList);
        } catch (Exception e) {
            log.error("获取启用检测规则失败", e);
            resp.setCode(500);
            resp.setMsg("获取失败: " + e.getMessage());
        }
        return resp;
    }

    public CommonServiceResponseVO<PageResultVO<SensitiveKeywordVO>> pageEnabledKeywords(KeywordPageQueryDTO query) {
        CommonServiceResponseVO<PageResultVO<SensitiveKeywordVO>> resp = new CommonServiceResponseVO<>();
        try {
            KeywordQueryDTO dto = new KeywordQueryDTO();
            dto.setStatus(query.status);
            dto.setPageNum(query.pageNum != null ? query.pageNum : 1);
            dto.setPageSize(query.pageSize != null ? query.pageSize : 10);

            Page<SensitiveKeyword> page = sensitiveKeywordService.page(dto);

            PageResultVO<SensitiveKeywordVO> pageResult = new PageResultVO<>();
            pageResult.total = page.getTotal();
            pageResult.current = page.getCurrent();
            pageResult.size = page.getSize();
            pageResult.pages = page.getPages();
            pageResult.records = new ArrayList<>();
            if (page.getRecords() != null) {
                for (SensitiveKeyword kw : page.getRecords()) {
                    SensitiveKeywordVO vo = new SensitiveKeywordVO();
                    vo.id = kw.getId();
                    vo.keyword = kw.getKeyword();
                    vo.category = kw.getCategory();
                    vo.severity = kw.getSeverity();
                    pageResult.records.add(vo);
                }
            }
            resp.setCode(200);
            resp.setData(pageResult);
        } catch (Exception e) {
            log.error("分页查询启用敏感词失败", e);
            resp.setCode(500);
            resp.setMsg("查询失败: " + e.getMessage());
        }
        return resp;
    }

    public static class KeywordPageQueryDTO {
        public String status;
        public Integer pageNum;
        public Integer pageSize;
    }

    public static class PageResultVO<T> {
        public List<T> records;
        public Long total;
        public Long current;
        public Long size;
        public Long pages;
    }

    public static class DetectionRuleVO {
        public Long id;
        public String ruleName;
        public String ruleType;
        public String ruleConfig;
        public Integer priority;
    }

    public static class SensitiveKeywordVO {
        public Long id;
        public String keyword;
        public String category;
        public String severity;
    }
}
