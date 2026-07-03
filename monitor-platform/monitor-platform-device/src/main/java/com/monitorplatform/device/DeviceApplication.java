package com.monitorplatform.device;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 设备管理模块启动类
 */
@SpringBootApplication(scanBasePackages = {"com.monitorplatform.device", "com.monitorplatform.common"})
@MapperScan("com.monitorplatform.device.mapper")
@EnableScheduling
@EnableAsync
@EnableDiscoveryClient
@EnableFeignClients
public class DeviceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeviceApplication.class, args);
        System.out.println("设备管理模块启动成功！");
    }
}
