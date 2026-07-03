package com.monitorplatform.websocket;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;

@SpringBootApplication(
        scanBasePackages = {"com.monitorplatform.websocket", "com.monitorplatform.common"},
        exclude = {
                DataSourceAutoConfiguration.class,
                LiquibaseAutoConfiguration.class,
                MybatisPlusAutoConfiguration.class
        })
public class WebSocketApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebSocketApplication.class, args);
    }
}
