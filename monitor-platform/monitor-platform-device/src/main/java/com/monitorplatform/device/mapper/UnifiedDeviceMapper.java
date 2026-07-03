package com.monitorplatform.device.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.monitorplatform.device.entity.UnifiedDevice;
import org.apache.ibatis.annotations.Mapper;

/**
 * 统一设备信息Mapper
 */
@Mapper
public interface UnifiedDeviceMapper extends BaseMapper<UnifiedDevice> {
}
