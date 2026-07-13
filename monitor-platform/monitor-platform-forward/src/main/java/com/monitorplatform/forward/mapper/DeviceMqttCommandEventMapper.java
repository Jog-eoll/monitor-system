package com.monitorplatform.forward.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.monitorplatform.forward.entity.DeviceMqttCommandEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeviceMqttCommandEventMapper extends BaseMapper<DeviceMqttCommandEvent> {
}
