package com.gateway.device.protocol.base.colorlight.standard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.device.protocol.base.colorlight.standard.model.vsn.VsnPrograms;
import com.gateway.device.protocol.common.JsonCustomMapper;
import com.gateway.device.protocol.common.JsonCustomMapperType;
import lombok.extern.slf4j.Slf4j;

/**
 * ColorLight LAN Player VSN 节目文件解析器/生成器。
 */
@Slf4j
public final class ColorLightVsnParser {

    private static final ObjectMapper MAPPER = JsonCustomMapper.get(JsonCustomMapperType.UPPER_CAMEL_CASE);

    private ColorLightVsnParser() {
    }

    /**
     * 将 VsnPrograms 对象序列化为 JSON 字节数组。
     */
    public static byte[] toJsonBytes(VsnPrograms programs) {
        if (programs == null) return new byte[0];
        try {
            return MAPPER.writeValueAsBytes(programs);
        } catch (Exception e) {
            log.error("Failed to serialize VSN: {}", e.getMessage(), e);
            return new byte[0];
        }
    }

    /**
     * 将 VsnPrograms 对象序列化为 JSON 字符串。
     */
    public static String toJson(VsnPrograms programs) {
        if (programs == null) return "{}";
        try {
            return MAPPER.writeValueAsString(programs);
        } catch (Exception e) {
            log.error("Failed to serialize VSN: {}", e.getMessage(), e);
            return "{}";
        }
    }

    /**
     * 从字节数组解析 VsnPrograms
     */
    public static VsnPrograms fromJsonBytes(byte[] jsonBytes) {
        if (jsonBytes == null || jsonBytes.length == 0) return null;
        try {
            return MAPPER.readValue(jsonBytes, VsnPrograms.class);
        } catch (Exception e) {
            log.error("Failed to parse VSN JSON: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 从字符串解析 VsnPrograms
     */
    public static VsnPrograms fromJson(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            return MAPPER.readValue(json, VsnPrograms.class);
        } catch (Exception e) {
            log.error("Failed to parse VSN JSON: {}", e.getMessage(), e);
            return null;
        }
    }
}
