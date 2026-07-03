package com.monitorplatform.ukey.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * Web MVC 配置
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private TokenInterceptor tokenInterceptor;

    /**
     * 白名单：无需 Token 的接口
     *
     * - /cert/validate       客户端证书校验（机器间调用）
     * - /cert/online         在线状态上报
     * - /cert/online-status  登录页轮询接口
     * - /cert/login          登录接口本身
     * - /cert/parse          证书文件解析（导入弹窗上传）
     * - /cert/import         导入证书（登录前也允许调用）
     * - /cert/bind-client    客户端绑定证书（初始化配置阶段调用）
     * - /cert/check-token    Token 校验接口
     * - /cert/heartbeat      客户端心跳接口
     * - /cert/disconnect     客户端断开通知
     * - /cert/admin-login
     * - /cert/temporary-authorization-info
     */
    private static final String[] WHITE_LIST = {
            "/cert/validate",
            "/cert/online",
            "/cert/online-status",
            "/cert/lock-status",
            "/cert/encrypt-key",
            "/cert/login",
            "/cert/disconnect",
            "/cert/parse",
            "/cert/import",
            "/cert/check-token",
            "/cert/heartbeat",
            "/cert/bind-client",
            "/cert/admin-login",
            "/cert/temporary-authorization-info"
    };

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tokenInterceptor)
                // 拦截所有 /cert/** 接口
                .addPathPatterns("/cert/**")

                // 白名单接口放行
                .excludePathPatterns(WHITE_LIST);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
    }
}
