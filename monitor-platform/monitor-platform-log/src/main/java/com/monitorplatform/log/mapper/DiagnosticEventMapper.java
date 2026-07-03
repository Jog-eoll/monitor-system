package com.monitorplatform.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.monitorplatform.log.entity.DiagnosticEventLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DiagnosticEventMapper extends BaseMapper<DiagnosticEventLog> {
}
