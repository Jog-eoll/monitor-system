package com.monitorplatform.device.handler.impl;

import com.monitorplatform.device.entity.dto.DeviceCommandDTO;
import com.monitorplatform.device.handler.CommandHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RebootCommandHandler implements CommandHandler {
    @Override
    public void handle(DeviceCommandDTO dto) {
        // 实现重启命令逻辑
        log.info("执行设备重启命令: {}", dto);
        // 具体的重启操作
    }

    @Override
    public String getCommandType() {
        return "reboot";
    }
}