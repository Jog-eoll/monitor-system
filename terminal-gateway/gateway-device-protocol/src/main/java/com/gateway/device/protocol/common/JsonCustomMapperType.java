package com.gateway.device.protocol.common;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public enum JsonCustomMapperType {
    /**
     * 默认
     */
    LOWER_CAMEL_CASE {
        @Override
        public void configure(ObjectMapper mapper) {
            configureBase(mapper);
            // 将 Java 驼峰字段转为首字母小写
            mapper.setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);
        }
    },
    LOWER_CAMEL_CASE_DEBUG {
        @Override
        public void configure(ObjectMapper mapper) {
            configureBase(mapper);
            // 将 Java 驼峰字段转为首字母小写
            mapper.setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);
            // 美化输出 【仅限测试】
            mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        }
    },
    UPPER_CAMEL_CASE {
        @Override
        public void configure(ObjectMapper mapper) {
            configureBase(mapper);
            // 将 Java 驼峰字段转为首字母大写
            mapper.setPropertyNamingStrategy(PropertyNamingStrategies.UPPER_CAMEL_CASE);
        }
    },
    UPPER_CAMEL_CASE_DEBUG {
        @Override
        public void configure(ObjectMapper mapper) {
            configureBase(mapper);
            // 将 Java 驼峰字段转为首字母大写
            mapper.setPropertyNamingStrategy(PropertyNamingStrategies.UPPER_CAMEL_CASE);
            // 美化输出 【仅限测试】
            mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        }
    },
    ;

    // 创建 ObjectMapper 的工厂方法
    public static ObjectMapper createMapper(JsonCustomMapperType type) {
        ObjectMapper mapper = new ObjectMapper();
        if (type == null) {
            LOWER_CAMEL_CASE.configure(mapper);
        } else {
            type.configure(mapper);
        }
        return mapper;
    }

    // 抽象方法：每个枚举值实现自己的配置逻辑
    protected abstract void configure(ObjectMapper mapper);

    // 公共配置组
    protected void configureBase(ObjectMapper mapper) {
        // 使用字段属性序列化，忽略getter/setter (必须配置，剔除Lombok影响)
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        // 非标准 JSON 语法支持: 允许字段名不带双引号
        mapper.configure(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES.mappedFeature(), true);
        // 非标准 JSON 语法支持: 允许单引号
        mapper.configure(JsonReadFeature.ALLOW_SINGLE_QUOTES.mappedFeature(), true);
        // 非标准 JSON 语法支持: 允许末尾逗号
        mapper.configure(JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature(), true);
        // 反序列化时,忽略 JSON 中存在的,但 Java 类没有对应字段的属性
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // 允许空对象
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        // 反序列化空值转成NULL
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        // 序列化去除NULL
        mapper.setDefaultPropertyInclusion(JsonInclude.Value.construct(
                JsonInclude.Include.NON_NULL, // 对象属性为 null 时不序列化
                JsonInclude.Include.ALWAYS // 集合数组的元素永远序列化
        ));
        // LocalDate/LocalDateTime 序列化: 使用 ISO-8601 格式序列化日期时间，而不是时间戳
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.registerModule(new JavaTimeModule());
        // 允许浮点数转 BigDecimal（避免精度丢失）
        mapper.configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);
        // 反序列化，大小写不敏感
        mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
    }
}
