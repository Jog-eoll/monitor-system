package com.vw.isds.register.service.impl;

import cn.hutool.crypto.asymmetric.KeyType;
import cn.hutool.crypto.asymmetric.RSA;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSON;
import com.monitorplatform.common.util.RedisUtil;
import com.vw.isds.register.entity.Authorization;
import com.vw.isds.register.entity.LicenseCheckModel;
import com.vw.isds.register.entity.ProjectInfo;
import com.vw.isds.register.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @Description: java类描述
 * @Author: zqr
 * @Date: 2024/1/30 11:35:22
 */
@Slf4j
@Service
public class RegisterServiceImpl implements RegisterService {

    @Resource
    ProjectService projectService;

    @Resource
    RedisUtil redisUtil;


    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");

    private static Authorization authorization;


    public static String msg = "";


    //    @Cacheable(value = "isLogin")
    @Override
    public boolean isLogin() {
        ProjectInfo info = projectService.info();
        if (info.getKey1() != null && info.getPublicKey() != null) {
            RSA rsa = new RSA(null, info.getPublicKey());
            String key1 = rsa.decryptStr(info.getKey1(), KeyType.PublicKey);
            LicenseCheckModel licenseCheckModel = JSON.parseObject(key1, LicenseCheckModel.class);
            log.info("获取授权：{}", JSONUtil.toJsonStr(licenseCheckModel));
            LicenseCheckModel currServerinfo = getCurrServerinfo();
            log.info("本机授权：{}", JSONUtil.toJsonStr(currServerinfo));
            if (!validMac(currServerinfo, licenseCheckModel.getMacAddress())) {
                msg = "mac地址校验失败";
                return false;
            }
            if (!currServerinfo.getCpuSerial().equals(licenseCheckModel.getCpuSerial())) {
                msg = "cpu校验失败";
                return false;
            }
            if (!currServerinfo.getMainBoardSerial().equals(licenseCheckModel.getMainBoardSerial())) {
                msg = "主板校验失败";
                return false;
            }
            //校验时间
            if (LocalDateTime.now().isBefore(licenseCheckModel.getAuthorization().getStartTime()) || LocalDateTime.now().isAfter(licenseCheckModel.getAuthorization().getEndTime())) {
                msg = "时间校验失败";
                return false;
            }
            //防止修改系统时间 每次登录都将时间存入redis
            Object loginTimeObj = redisUtil.get("loginTime");
            if (loginTimeObj != null) {
                LocalDateTime loginTime = LocalDateTime.parse(loginTimeObj.toString());
                if (LocalDateTime.now().isBefore(loginTime)) {
                    return false;
                }
            }
            redisUtil.set("loginTime", LocalDateTime.now().toString());
            authorization = licenseCheckModel.getAuthorization();
            return true;
        }
        return false;
    }

    @Override
    public int cameraCount() {
        return authorization.getDeviceCount().getCamera();
    }

    @Override
    public int doorCount() {
        return authorization.getDeviceCount().getDoor();
    }

    @Override
    public int alarmCount() {
        return authorization.getDeviceCount().getAlarm();
    }

    @Override
    public int decoderChannelCount() {
        return authorization.getDeviceCount().getDecoderChannel();
    }

    @Override
    public int securityChannelCount() {
        return authorization.getDeviceCount().getSecurityChannel();
    }

    @Override
    public int ivsChannelCount() {
        return authorization.getDeviceCount().getIvsChannel();
    }

    @Override
    public int environmentChannelCount() {
        return authorization.getDeviceCount().getEnvironmentChannel();
    }

    @Override
    public int userCount() {
        return authorization.getUserCount();
    }

    private boolean validMac(LicenseCheckModel checkModel, List<String> macs) {
        for (String macAddress : checkModel.getMacAddress()) {
            if (macs.contains(macAddress)) {
                return true;
            }
        }
        return false;
    }

    private LicenseCheckModel getCurrServerinfo() {
        String osName = System.getProperty("os.name").toLowerCase();
        AbstractServerInfos abstractServerInfos;
        if (osName.startsWith("windows")) {
            abstractServerInfos = new WindowsServerInfos();
        } else if (osName.startsWith("linux")) {
            abstractServerInfos = new LinuxServerInfos();
        } else {
            abstractServerInfos = new LinuxServerInfos();
        }
        return abstractServerInfos.getServerInfos();
    }

    @Override
    public int crossCount() {
        return authorization.getDeviceCount().getCrossing();
    }
}
