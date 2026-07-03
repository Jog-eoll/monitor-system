package com.monitorplatform.forward.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.monitorplatform.forward.entity.GatewayDispatchLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 网关配置下发记录Mapper
 */
@Mapper
public interface GatewayDispatchLogMapper extends BaseMapper<GatewayDispatchLog> {
}
