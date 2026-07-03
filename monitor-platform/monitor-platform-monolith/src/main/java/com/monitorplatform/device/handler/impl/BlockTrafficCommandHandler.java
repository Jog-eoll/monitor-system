package com.monitorplatform.device.handler.impl;

import com.monitorplatform.device.entity.dto.DeviceCommandDTO;
import com.monitorplatform.device.handler.CommandHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BlockTrafficCommandHandler implements CommandHandler {
    @Override
    public void handle(DeviceCommandDTO dto) {
        // 实现流量阻断命令逻辑
        log.info("执行流量阻断命令: {}", dto);
        // 具体的流量阻断操作
    }

    @Override
    public String getCommandType() {
        return "block_traffic";
    }
}