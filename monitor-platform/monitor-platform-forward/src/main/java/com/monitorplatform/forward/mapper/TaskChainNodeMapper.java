package com.monitorplatform.forward.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.monitorplatform.forward.entity.TaskChainNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskChainNodeMapper extends BaseMapper<TaskChainNode> {

    int updateDeviceIpByDeviceId(@Param("deviceId") String deviceId,
                                  @Param("newIp") String newIp);
}
