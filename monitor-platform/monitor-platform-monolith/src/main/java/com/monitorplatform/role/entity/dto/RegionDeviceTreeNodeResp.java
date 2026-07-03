package com.monitorplatform.role.entity.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RegionDeviceTreeNodeResp {

    /** REGION 或 DEVICE */
    private String nodeType;
    /** REGION：区域 id；DEVICE：unified_device_info.id */
    private Long id;
    private String name;
    private String code;
    private Integer sort;
    /** 仅 REGION */
    private Long parentId;
    /** 仅 DEVICE：所属区域 id */
    private Long location;
    /** DEVICE：业务设备标识（SN 等） */
    private String deviceId;
    /** DEVICE：设备类型 */
    private String deviceType;
    /** DEVICE: IP address */
    private String ipAddress;
    private List<RegionDeviceTreeNodeResp> children = new ArrayList<>();
}
