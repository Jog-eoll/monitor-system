package com.publishgateway.udpproxy;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication // 移除 (exclude = {DataSourceAutoConfiguration.class...})
@MapperScan("com.publishgateway.udpproxy.mapper") // 添加扫描路径
@EnableScheduling
public class UdpProxyGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(UdpProxyGatewayApplication.class, args);
    }
}
