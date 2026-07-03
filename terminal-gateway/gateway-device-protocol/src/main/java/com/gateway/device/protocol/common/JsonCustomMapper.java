package com.gateway.device.protocol.common;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Jackson ObjectMapper(单例、线程安全，模块内所有 JSON 操作共用此实例)
 */
public final class JsonCustomMapper {

    private static final ConcurrentHashMap<JsonCustomMapperType, ObjectMapper> STORAGE = new ConcurrentHashMap<>();

    private JsonCustomMapper() {
    }

    public static ObjectMapper get() {
        return get(JsonCustomMapperType.LOWER_CAMEL_CASE);
    }

    public static ObjectMapper get(JsonCustomMapperType type) {
        JsonCustomMapperType effectiveType = type == null ? JsonCustomMapperType.LOWER_CAMEL_CASE : type;
        return STORAGE.computeIfAbsent(effectiveType, JsonCustomMapperType::createMapper);
    }
}
