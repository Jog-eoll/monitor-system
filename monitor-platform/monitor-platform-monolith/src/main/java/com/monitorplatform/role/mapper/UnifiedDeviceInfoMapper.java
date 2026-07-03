package com.monitorplatform.role.mapper;

import com.monitorplatform.role.entity.dto.UnifiedDeviceInfoTreeRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UnifiedDeviceInfoMapper {

    /**
     * 只拉拼树所需字段，按 region_id 关联区域。
     */
    @Select("SELECT id, device_id AS deviceId, device_name AS deviceName, device_type AS deviceType, ip_address AS ipAddress, region_id AS regionId " +
            "FROM unified_device_info " +
            "WHERE region_id IS NOT NULL")
    List<UnifiedDeviceInfoTreeRow> selectAllForRegionDeviceTree();
}
