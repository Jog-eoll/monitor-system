package com.monitorplatform.alarm.websocket;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring 上下文持有者
 * 解决 @ServerEndpoint 实例由容器管理、无法直接 @Autowired 的问题
 * 通过静态方法在 @ServerEndpoint 的回调中获取 Spring Bean
 */
@Component
public class SpringContextHolder implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        SpringContextHolder.applicationContext = ctx;
    }

    /**
     * 获取 Spring Bean
     *
     * @param clazz Bean 类型
     * @param <T>   泛型
     * @return Bean 实例，上下文未就绪时返回 null
     */
    public static <T> T getBean(Class<T> clazz) {
        if (applicationContext == null) {
            return null;
        }
        try {
            return applicationContext.getBean(clazz);
        } catch (Exception e) {
            return null;
        }
    }
}
