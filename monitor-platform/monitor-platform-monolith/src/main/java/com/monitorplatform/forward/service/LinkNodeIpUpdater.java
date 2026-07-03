package com.monitorplatform.forward.service;

import com.monitorplatform.forward.mapper.TaskChainNodeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
public class LinkNodeIpUpdater {

    @Resource
    private TaskChainNodeMapper taskChainNodeMapper;

    public int updateChainNodeIp(String deviceId, String newIp) {
        try {
            int count = taskChainNodeMapper.updateDeviceIpByDeviceId(deviceId, newIp);
            return count;
        } catch (Exception e) {
            log.error("[LinkNodeIpUpdater] failed to update chain node IP. deviceId={}, newIp={}", deviceId, newIp, e);
            return 0;
        }
    }
}
