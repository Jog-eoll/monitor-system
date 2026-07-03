package com.gateway.device.protocol.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Jackson JsonNode 工具方法，协议模块内复用。
 */
public final class JsonNodeUtils {

    private JsonNodeUtils() {
    }

    // ════════════════════════════════════════════════════════════
    // 数字字段归一化
    // ════════════════════════════════════════════════════════════

    /**
     * 将 JsonNode 中指定数字字段归一化为 int，避免 SDK 返回的 double 被 Jackson
     * 序列化为科学计数法（如 6E+1）。
     *
     * @param data 原始 JSON（非 ObjectNode 时原样返回）
     *             默认 "ratio"
     */
    public static JsonNode normalizeNumberFields(JsonNode data) {
        return normalizeNumberFields(data, "ratio");
    }

    /**
     * 将 JsonNode 中指定数字字段归一化为 int，避免 SDK 返回的 double 被 Jackson
     * 序列化为科学计数法（如 6E+1）。
     *
     * @param data   原始 JSON（非 ObjectNode 时原样返回）
     * @param fields 需归一化的字段名，默认 "ratio"
     */
    public static JsonNode normalizeNumberFields(JsonNode data, String... fields) {
        if (!(data instanceof ObjectNode)) {
            return data;
        }
        if (fields.length == 0) {
            return data;
        }
        ObjectNode obj = (ObjectNode) data;
        for (String field : fields) {
            if (obj.has(field)) {
                JsonNode node = obj.get(field);
                if (node.isNumber()) {
                    obj.put(field, node.asInt());
                }
            }
        }
        return obj;
    }
}
