package com.infopublish.client.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.infopublish.client.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
