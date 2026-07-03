package com.vw.isds.register.entity;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @Description: 服务器硬件信息
 * @Author: zqr
 * @Date: 2024/2/2 11:06:50
 */
@Data
public class LicenseCheckModel implements Serializable {

    private static final long serialVersionUID = 8600137500316662317L;

    /**
     * 可被允许的MAC地址
     */
    private List<String> macAddress;

    /**
     * 可被允许的CPU序列号
     */
    private String cpuSerial;

    /**
     * 可被允许的主板序列号
     */
    private String mainBoardSerial;


    /**
     * 应用授权信息
     */
    private Authorization authorization;

}
