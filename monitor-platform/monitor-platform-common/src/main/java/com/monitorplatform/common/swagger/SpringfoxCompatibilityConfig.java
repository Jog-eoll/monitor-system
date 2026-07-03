package com.monitorplatform.common.swagger;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;
import springfox.documentation.spring.web.plugins.WebMvcRequestHandlerProvider;

import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@ConditionalOnClass(name = "springfox.documentation.spring.web.plugins.WebMvcRequestHandlerProvider")
@ConditionalOnProperty(name = "isds.swagger2.enable", havingValue = "true", matchIfMissing = false)
public class SpringfoxCompatibilityConfig implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof WebMvcRequestHandlerProvider) {
            customizeSpringfoxHandlerMappings(getHandlerMappings(bean));
        }
        return bean;
    }

    private <T extends RequestMappingInfoHandlerMapping> void customizeSpringfoxHandlerMappings(List<T> mappings) {
        List<T> copy = mappings.stream()
                .filter(mapping -> mapping.getPatternParser() == null)
                .collect(Collectors.toList());
        mappings.clear();
        mappings.addAll(copy);
    }

    @SuppressWarnings("unchecked")
    private List<RequestMappingInfoHandlerMapping> getHandlerMappings(Object bean) {
        Field field = ReflectionUtils.findField(bean.getClass(), "handlerMappings");
        if (field == null) {
            throw new IllegalStateException("Failed to find handlerMappings field in " + bean.getClass());
        }
        ReflectionUtils.makeAccessible(field);
        try {
            return (List<RequestMappingInfoHandlerMapping>) field.get(bean);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to access handlerMappings field", e);
        }
    }
}
