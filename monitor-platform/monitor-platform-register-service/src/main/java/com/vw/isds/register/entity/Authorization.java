package com.vw.isds.register.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Description: 具体授权信息
 * @Author: zqr
 * @Date: 2024/2/19 18:30:41
 */
@Data
public class Authorization {

    /**
     * 项目id
     */
    private String projectId;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 使用结束时间
     */
    private LocalDateTime endTime;

    /**
     * 菜单权限
     */
    private List<SysResource> resources;

    /**
     * 设备数量权限
     */
    private DeviceCount deviceCount;


    /**
     * 人员数量权限
     */
    private int userCount;
}
