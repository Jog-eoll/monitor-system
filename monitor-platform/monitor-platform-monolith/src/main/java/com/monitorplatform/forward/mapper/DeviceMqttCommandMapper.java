package com.monitorplatform.forward.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.monitorplatform.forward.entity.DeviceMqttCommand;
import org.apache.ibatis.annotations.Mapper;

/**
 * MQTT 命令下发记录 Mapper
 */
@Mapper
public interface DeviceMqttCommandMapper extends BaseMapper<DeviceMqttCommand> {
}
