package com.vw.isds.register.service;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Description: java类描述
 * @Author: zqr
 * @Date: 2024/1/30 11:28:17
 */
@RestController
@RequestMapping("/register")
public interface RegisterService {

    @GetMapping("/isLogin")
    boolean isLogin();


    @GetMapping("/cameraCount")
    int cameraCount();

    @GetMapping("/doorCount")
    int doorCount();

    @GetMapping("/alarmCount")
    int alarmCount();

    @GetMapping("/decoderChannelCount")
    int decoderChannelCount();

    @GetMapping("/securityChannelCount")
    int securityChannelCount();

    @GetMapping("/ivsChannelCount")
    int ivsChannelCount();

    @GetMapping("/environmentChannelCount")
    int environmentChannelCount();

    @GetMapping("/userCount")
    int userCount();

    @GetMapping("/crossCount")
    int crossCount();
}
