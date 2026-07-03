package com.monitorplatform.role;


import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 角色用户菜单管理模块启动类
 */
@SpringBootApplication(scanBasePackages = {"com.monitorplatform.role", "com.monitorplatform.common"})
@MapperScan("com.monitorplatform.role.mapper")
@EnableScheduling
@EnableAsync
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.monitorplatform.role", "com.monitorplatform.common.service"})
public class RoleApplication {

    public static void main(String[] args) {
        SpringApplication.run(RoleApplication.class, args);
        System.out.println("========================================");
        System.out.println("  Role管理模块启动成功！");
        System.out.println("========================================");
    }
}
