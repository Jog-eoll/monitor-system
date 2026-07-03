package com.monitorplatform.alarm;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 告警管理模块启动类
 */
@SpringBootApplication(scanBasePackages = {"com.monitorplatform.alarm", "com.monitorplatform.common"})
@MapperScan("com.monitorplatform.alarm.mapper")
@EnableDiscoveryClient
@EnableFeignClients
@EnableAsync
public class AlarmApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(AlarmApplication.class, args);
        System.out.println("========================================");
        System.out.println("  告警管理模块启动成功！");
        System.out.println("========================================");
    }
}
