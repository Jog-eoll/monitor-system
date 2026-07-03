package com.infopublish.client;


import com.infopublish.client.utils.log.LogEncodingSupport;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@EnableScheduling  // 启用定时任务（用于状态监控）
public class InfoPublishClientApplication {

    public static void main(String[] args) {
        LogEncodingSupport.installIfEnabled(args);
        ConfigurableApplicationContext ctx = SpringApplication.run(InfoPublishClientApplication.class, args);
        String port = ctx.getEnvironment().getProperty("server.port", "7080");
        String url = "http://localhost:" + port;

        System.out.println("========================================");
        System.out.println("  信息发布监管客户端启动成功！");
        System.out.println("  配置页面: " + url);
        System.out.println("========================================");
    }
}
