package com.monitorplatform.rule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.monitorplatform.rule.entity.DetectionRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 检测规则Mapper
 */
@Mapper
public interface DetectionRuleMapper extends BaseMapper<DetectionRule> {
}
