package com.monitorplatform.upgrade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.monitorplatform.upgrade.entity.RemoteUpgradeTaskDevice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface RemoteUpgradeTaskDeviceMapper extends BaseMapper<RemoteUpgradeTaskDevice> {

    List<Map<String, Object>> selectDeviceVersions(@Param("targetDeviceId") String targetDeviceId);

    Map<String, Object> selectLatestSuccessVersion(@Param("targetDeviceId") String targetDeviceId);

}
