package com.monitorplatform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 监控平台 - 单体融合应用启动类
 * 融合模块: device, alarm, forward, content, rule, websocket, role, ukey, registry, register
 * 公共模块: monitor-platform-common (作为依赖引入)
 */
@SpringBootApplication(
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class,
        scanBasePackages = {
        "com.monitorplatform.monolith",
        "com.monitorplatform.device",
        "com.monitorplatform.alarm",
        "com.monitorplatform.forward",
        "com.monitorplatform.content",
        "com.monitorplatform.rule",
        "com.monitorplatform.websocket",
        "com.monitorplatform.role",
        "com.monitorplatform.ukey",
        "com.monitorplatform.registry",
        "com.monitorplatform.upgrade",
        "com.monitorplatform.log",
        "com.monitorplatform.common",
        "com.vw.isds.register"
})
@MapperScan(basePackages = {
        "com.monitorplatform.device.mapper",
        "com.monitorplatform.alarm.mapper",
        "com.monitorplatform.forward.mapper",
        "com.monitorplatform.content.mapper",
        "com.monitorplatform.rule.mapper",
        "com.monitorplatform.role.mapper",
        "com.monitorplatform.ukey.mapper",
        "com.monitorplatform.registry.mapper",
        "com.monitorplatform.upgrade.mapper",
        "com.monitorplatform.log.mapper"
})
@EnableScheduling
@EnableAsync
public class MonolithApplication {

    public static void main(String[] args) {
        SpringApplication.run(MonolithApplication.class, args);
        System.out.println("========================================");
        System.out.println("  监控平台单体融合应用启动成功！");
        System.out.println("========================================");
    }
}
