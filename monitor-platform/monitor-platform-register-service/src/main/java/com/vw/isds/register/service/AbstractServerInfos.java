package com.vw.isds.register.service;

import com.vw.isds.register.entity.LicenseCheckModel;
import lombok.extern.slf4j.Slf4j;

import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * @Description: java类描述
 * @Author: zqr
 * @Date: 2024/2/2 11:16:12
 */
@Slf4j
public abstract class AbstractServerInfos {

    /**
     * 组装需要额外校验的License参数
     *
     * @return LicenseCheckModel
     * @author zqr
     * @date 2024/2/2 11:16:12
     * @since 1.0.0
     */
    public LicenseCheckModel getServerInfos() {
        LicenseCheckModel result = new LicenseCheckModel();
        try {
            result.setMacAddress(this.getMacAddress());
            result.setCpuSerial(this.getCPUSerial());
            result.setMainBoardSerial(this.getMainBoardSerial());
        } catch (Exception e) {
            log.error("获取服务器硬件信息失败", e);
        }

        return result;
    }


    /**
     * 获取Mac地址
     *
     * @return java.util.List<java.lang.String>
     * @author zqr
     * @date 2024/2/2 11:16:12
     * @since 1.0.0
     */
    protected abstract List<String> getMacAddress() throws Exception;

    /**
     * 获取CPU序列号
     *
     * @return java.lang.String
     * @author zqr
     * @date 2024/2/2 11:16:12
     * @since 1.0.0
     */
    protected abstract String getCPUSerial() throws Exception;

    /**
     * 获取主板序列号
     *
     * @return java.lang.String
     * @author zqr
     * @date 2024/2/2 11:16:12
     * @since 1.0.0
     */
    protected abstract String getMainBoardSerial() throws Exception;


    /**
     * 获取mac地址
     *
     * @param
     * @author zqr
     * @date 2024/2/2 11:16:12
     * @since 1.0.0
     */
    protected List<String> getMac() {
        try {
            List<String> macs = new ArrayList<>();
            Enumeration<NetworkInterface> allNetInterfaces = NetworkInterface.getNetworkInterfaces();
            byte[] mac;
            while (allNetInterfaces.hasMoreElements()) {
                NetworkInterface netInterface = allNetInterfaces.nextElement();
                if (netInterface.isLoopback() || netInterface.isVirtual() || netInterface.isPointToPoint() || !netInterface.isUp()) {
                } else {
                    StringBuilder macAddress = new StringBuilder();
                    mac = netInterface.getHardwareAddress();
                    if (mac != null) {
                        for (int i = 0; i < mac.length; i++) {
                            String format = String.format("%02X%s", mac[i], "-");
                            if (i == mac.length - 1) {
                                format = String.format("%02X", mac[i]);
                            }
                            macAddress.append(format);
                        }
                    }
                    macs.add(macAddress.toString());
                }
            }
            return macs;
        } catch (Exception e) {
            log.info("获取mac出错：{}", e.getMessage());
            return Collections.emptyList();
        }
    }
}



