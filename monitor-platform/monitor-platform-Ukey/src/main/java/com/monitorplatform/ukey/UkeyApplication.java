package com.monitorplatform.ukey;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ukey管理模块启动类
 */
@SpringBootApplication(scanBasePackages = {"com.monitorplatform.ukey", "com.monitorplatform.common"})
@MapperScan("com.monitorplatform.ukey.mapper")
@EnableScheduling
@EnableFeignClients(basePackages = {"com.monitorplatform.ukey", "com.monitorplatform.common.service"})
public class UkeyApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(UkeyApplication.class, args);
        System.out.println("========================================");
        System.out.println("  Ukey管理模块启动成功！");
        System.out.println("========================================");
    }
}
