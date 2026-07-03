package com.monitorplatform.content.service;

import com.monitorplatform.content.feign.RuleFeignClient;
import com.monitorplatform.content.entity.vo.CommonServiceResponseVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class RuleConfigService {

    private final RuleFeignClient ruleFeignClient;

    public RuleConfigService(RuleFeignClient ruleFeignClient) {
        this.ruleFeignClient = ruleFeignClient;
    }

    public List<DetectionRuleVO> getEnabledDetectionRules() {
        try {
            CommonServiceResponseVO<List<RuleFeignClient.DetectionRuleVO>> resp = ruleFeignClient.listEnabledDetectionRules();
            if (resp != null && resp.getCode() != null && resp.getCode() == 200 && resp.getData() != null) {
                List<DetectionRuleVO> rules = new ArrayList<>();
                for (RuleFeignClient.DetectionRuleVO item : resp.getData()) {
                    DetectionRuleVO vo = new DetectionRuleVO();
                    vo.setId(item.id);
                    vo.setRuleName(item.ruleName);
                    vo.setRuleType(item.ruleType);
                    vo.setRuleConfig(item.ruleConfig);
                    vo.setPriority(item.priority);
                    rules.add(vo);
                }
                log.info("获取启用检测规则成功, count={}", rules.size());
                return rules;
            }
        } catch (Exception e) {
            log.warn("调用规则服务失败(getEnabledDetectionRules)", e);
        }
        return new ArrayList<>();
    }

    public List<SensitiveKeywordVO> getEnabledKeywords() {
        try {
            RuleFeignClient.KeywordPageQueryDTO query = new RuleFeignClient.KeywordPageQueryDTO();
            query.status = "enabled";
            query.pageNum = 1;
            query.pageSize = 1000;

            CommonServiceResponseVO<RuleFeignClient.PageResultVO<RuleFeignClient.SensitiveKeywordVO>> resp =
                    ruleFeignClient.pageEnabledKeywords(query);
            if (resp != null && resp.getCode() != null && resp.getCode() == 200 && resp.getData() != null
                    && resp.getData().records != null) {
                List<SensitiveKeywordVO> keywords = new ArrayList<>();
                for (RuleFeignClient.SensitiveKeywordVO item : resp.getData().records) {
                    SensitiveKeywordVO vo = new SensitiveKeywordVO();
                    vo.setId(item.id);
                    vo.setKeyword(item.keyword);
                    vo.setCategory(item.category);
                    vo.setSeverity(item.severity);
                    keywords.add(vo);
                }
                log.info("获取启用敏感词成功, count={}", keywords.size());
                return keywords;
            }
        } catch (Exception e) {
            log.warn("调用规则服务失败(getEnabledKeywords)", e);
        }
        return new ArrayList<>();
    }

    public static class DetectionRuleVO {
        private Long id;
        private String ruleName;
        private String ruleType;
        private String ruleConfig;
        private Integer priority;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getRuleName() {
            return ruleName;
        }

        public void setRuleName(String ruleName) {
            this.ruleName = ruleName;
        }

        public String getRuleType() {
            return ruleType;
        }

        public void setRuleType(String ruleType) {
            this.ruleType = ruleType;
        }

        public String getRuleConfig() {
            return ruleConfig;
        }

        public void setRuleConfig(String ruleConfig) {
            this.ruleConfig = ruleConfig;
        }

        public Integer getPriority() {
            return priority;
        }

        public void setPriority(Integer priority) {
            this.priority = priority;
        }
    }

    public static class SensitiveKeywordVO {
        private Long id;
        private String keyword;
        private String category;
        private String severity;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getKeyword() {
            return keyword;
        }

        public void setKeyword(String keyword) {
            this.keyword = keyword;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }
    }
}
