package com.monitorplatform.device.handler.impl;

import com.monitorplatform.device.entity.dto.DeviceCommandDTO;
import com.monitorplatform.device.handler.CommandHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UpgradeCommandHandler implements CommandHandler {
    @Override
    public void handle(DeviceCommandDTO dto) {
        // 实现升级命令逻辑
        log.info("执行升级命令: {}", dto);
        // 具体的升级操作
    }

    @Override
    public String getCommandType() {
        return "upgrade";
    }
}