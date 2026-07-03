package com.vw.isds.register.entity;

import lombok.Data;

/**
 * @Description: 设备数量权限控制
 * @Author: zqr
 * @Date: 2024/2/20 11:25:01
 */
@Data
public class DeviceCount {

    private int door;

    private int camera;

    private int alarm;

    /**
     * 视频解码
     */
    private int decoderChannel;

    /**
     * 安检主机
     */
    private int securityChannel;

    /**
     * 智能主机
     */
    private int ivsChannel;

    /**
     * 环境主机
     */
    private int environmentChannel;


    private int crossing;
}
