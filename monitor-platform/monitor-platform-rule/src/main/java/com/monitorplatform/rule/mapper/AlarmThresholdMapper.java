package com.monitorplatform.rule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.monitorplatform.rule.entity.AlarmThreshold;
import org.apache.ibatis.annotations.Mapper;

/**
 * 告警阈值Mapper
 */
@Mapper
public interface AlarmThresholdMapper extends BaseMapper<AlarmThreshold> {
}
