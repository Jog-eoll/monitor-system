package com.monitorplatform.device.entity.dto;

import lombok.Data;

import java.util.List;

/**
 * 设备分类树节点DTO
 */
@Data
public class DeviceTreeNodeDTO {
    
    /**
     * 节点ID
     */
    private String id;
    
    /**
     * 节点名称
     */
    private String label;
    
    /**
     * 节点类型: category(分类) / device(设备)
     */
    private String type;
    
    /**
     * 设备类型: camera/gateway/server/terminal
     */
    private String deviceType;
    
    /**
     * 设备数量（仅分类节点有效）
     */
    private Integer count;
    
    /**
     * 在线数量
     */
    private Integer onlineCount;
    
    /**
     * 离线数量
     */
    private Integer offlineCount;
    
    /**
     * 告警数量
     */
    private Integer alarmCount;
    
    /**
     * 子节点
     */
    private List<DeviceTreeNodeDTO> children;
}
