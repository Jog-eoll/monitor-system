package com.monitorplatform.log;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.monitorplatform.log", "com.monitorplatform.common"})
@MapperScan("com.monitorplatform.log.mapper")
@EnableDiscoveryClient
@EnableFeignClients
@EnableScheduling
public class LogApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogApplication.class, args);
        System.out.println("========================================");
        System.out.println(" Log日志管理模块启动成功！");
        System.out.println("========================================");
    }
}
