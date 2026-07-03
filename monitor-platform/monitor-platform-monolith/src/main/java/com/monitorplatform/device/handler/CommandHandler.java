package com.monitorplatform.device.handler;

import com.monitorplatform.device.entity.dto.DeviceCommandDTO;

/**
 * 设备命令处理器接口
 */
public interface CommandHandler {
    /**
     * 处理设备命令
     * @param dto 设备命令DTO
     */
    void handle(DeviceCommandDTO dto);
    
    /**
     * 获取命令类型
     * @return 命令类型
     */
    String getCommandType();
}