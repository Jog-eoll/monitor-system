package com.monitorplatform.device.handler.impl;

import com.monitorplatform.device.entity.dto.DeviceCommandDTO;
import com.monitorplatform.device.handler.CommandHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BlackscreenCommandHandler implements CommandHandler {
    @Override
    public void handle(DeviceCommandDTO dto) {
        // 实现黑屏命令逻辑
        log.info("执行黑屏命令: {}", dto);
        // 具体的黑屏操作
    }

    @Override
    public String getCommandType() {
        return "blackscreen";
    }
}