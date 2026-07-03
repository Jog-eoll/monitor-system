package com.vw.isds.register;

import org.springframework.boot.SpringApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.SpringCloudApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * @Description: java类描述
 * @Author: zqr
 * @Date: 2024/1/30 10:20:02
 */
@SpringCloudApplication
@EnableCaching
@EnableFeignClients("com.vw")
@ComponentScan("com.vw")
public class RegisterApplication {

    public static void main(String[] args) {
        SpringApplication.run(RegisterApplication.class, args);
    }
}
