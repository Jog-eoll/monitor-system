package com.monitorplatform.registry.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.monitorplatform.registry.entity.ServiceInstance;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ServiceInstanceMapper extends BaseMapper<ServiceInstance> {
}