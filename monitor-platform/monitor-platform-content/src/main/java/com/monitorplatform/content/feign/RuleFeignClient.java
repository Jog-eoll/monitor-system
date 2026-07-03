package com.monitorplatform.content.feign;

import com.monitorplatform.content.entity.vo.CommonServiceResponseVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "monitor-rule")
public interface RuleFeignClient {

    @GetMapping("/rule/detection/list-enabled")
    CommonServiceResponseVO<List<DetectionRuleVO>> listEnabledDetectionRules();

    @PostMapping("/rule/keyword/page")
    CommonServiceResponseVO<PageResultVO<SensitiveKeywordVO>> pageEnabledKeywords(@RequestBody KeywordPageQueryDTO query);

    class KeywordPageQueryDTO {
        public String status;
        public Integer pageNum;
        public Integer pageSize;
    }

    class PageResultVO<T> {
        public List<T> records;
        public Long total;
        public Long current;
        public Long size;
        public Long pages;
    }

    class DetectionRuleVO {
        public Long id;
        public String ruleName;
        public String ruleType;
        public String ruleConfig;
        public Integer priority;
    }

    class SensitiveKeywordVO {
        public Long id;
        public String keyword;
        public String category;
        public String severity;
    }
}
