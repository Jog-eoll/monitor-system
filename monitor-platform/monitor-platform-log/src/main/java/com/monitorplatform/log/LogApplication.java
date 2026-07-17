package com.monitorplatform.log;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 在 monolith 模式下通过 @Profile("!monolith") 禁用，避免其 @ComponentScan 对
 * com.monitorplatform.log / com.monitorplatform.common 进行二次扫描（使用默认
 * AnnotationBeanNameGenerator），与 MonolithApplication 的 FullyQualified 命名冲突。
 */
@Profile("!monolith")
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
