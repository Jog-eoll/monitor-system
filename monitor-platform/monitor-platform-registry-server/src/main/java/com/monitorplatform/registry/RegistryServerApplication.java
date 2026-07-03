package com.monitorplatform.registry;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.monitorplatform.registry")
@EnableScheduling
@MapperScan("com.monitorplatform.registry.mapper")
public class RegistryServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(RegistryServerApplication.class, args);
    }
}
