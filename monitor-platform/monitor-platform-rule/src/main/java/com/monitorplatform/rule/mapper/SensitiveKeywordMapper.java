package com.monitorplatform.rule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.monitorplatform.rule.entity.SensitiveKeyword;
import org.apache.ibatis.annotations.Mapper;

/**
 * 敏感关键词Mapper
 */
@Mapper
public interface SensitiveKeywordMapper extends BaseMapper<SensitiveKeyword> {
}
