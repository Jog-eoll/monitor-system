package com.monitorplatform.rule;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 规则配置模块启动类
 */
@SpringBootApplication(scanBasePackages = {"com.monitorplatform.rule", "com.monitorplatform.common"})
@MapperScan("com.monitorplatform.rule.mapper")
@EnableDiscoveryClient
public class RuleApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(RuleApplication.class, args);
        System.out.println("====== 规则配置模块启动成功 ======");
    }
}
