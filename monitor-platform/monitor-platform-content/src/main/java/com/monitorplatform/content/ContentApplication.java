package com.monitorplatform.content;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 内容识别模块启动类
 */
@SpringBootApplication(scanBasePackages = {"com.monitorplatform.content", "com.monitorplatform.common"})
@MapperScan("com.monitorplatform.content.mapper")
@EnableDiscoveryClient
@EnableFeignClients
@EnableAsync
public class ContentApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(ContentApplication.class, args);
        System.out.println("====== 内容识别模块启动成功 ======");
    }
}
