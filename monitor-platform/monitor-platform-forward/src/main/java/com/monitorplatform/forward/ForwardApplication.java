package com.monitorplatform.forward;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 网关转发配置管理模块启动类
 */
@SpringBootApplication(scanBasePackages = {"com.monitorplatform.forward", "com.monitorplatform.upgrade", "com.monitorplatform.common"})
@MapperScan({"com.monitorplatform.forward.mapper", "com.monitorplatform.upgrade.mapper"})
@EnableScheduling
@EnableDiscoveryClient
@EnableFeignClients
public class ForwardApplication {
    public static void main(String[] args) {
        SpringApplication.run(ForwardApplication.class, args);
    }
}
