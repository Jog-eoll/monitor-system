package com.gateway.udpproxy;


import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {
        "com.gateway.udpproxy",
        "com.gateway.crypto",
        "com.gateway.auth",
        "com.gateway.device.protocol",
        "com.gateway.device.core"
})
@MapperScan("com.gateway.udpproxy.mapper")

public class TerminalGatewayApplication {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    public static void main(String[] args) {
        SpringApplication.run(TerminalGatewayApplication.class, args);
    }
}
