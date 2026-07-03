package com.gateway.device.protocol.base.novastar.viplexcore.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.gateway.device.protocol.base.novastar.viplexcore.SdkFunction;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexCoreChannel;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexResponse;
import com.gateway.device.protocol.common.JsonCustomMapper;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

/**
 * ViplexCore SDK 节目查询辅助 —— 静态工具类。
 */
@Slf4j
public final class ViplexCoreProgramHelper {

    private ViplexCoreProgramHelper() {
    }

    /**
     * 查询终端节目列表，返回 {@code programInfos} 数组节点，失败返回 null。
     */
    public static JsonNode queryProgramInfos(ViplexCoreChannel channel,
                                             String sn, Duration timeout) {
        String json = JsonCustomMapper.get().createObjectNode()
                .put("sn", sn).toString();
        ViplexResponse resp = channel.execute(SdkFunction.NV_GET_PROGRAM_INFO_ASYNC, json, timeout);
        if (!resp.isSuccess()) return null;
        try {
            JsonNode root = JsonCustomMapper.get().readTree(resp.getData());
            return root.path("programInfos");
        } catch (Exception e) {
            log.warn("解析节目列表 JSON 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 在节目信息数组中查找匹配名称且 {@code statusCode==0} 的 identifier，未找到返回 null。
     */
    public static String findProgramIdentifier(JsonNode programInfos, String name) {
        if (programInfos == null || !programInfos.isArray()) return null;
        for (JsonNode item : programInfos) {
            if (name.equals(item.path("name").asText(null))
                    && item.path("statusCode").asInt(-1) == 0) {
                return item.path("identifier").asText(null);
            }
        }
        return null;
    }

    /**
     * 在节目信息数组中按 identifier 直接匹配，确认该节目存在。
     */
    public static boolean hasProgramIdentifier(JsonNode programInfos, String identifier) {
        if (programInfos == null || !programInfos.isArray()) return false;
        for (JsonNode item : programInfos) {
            if (identifier.equals(item.path("identifier").asText(null))) {
                return true;
            }
        }
        return false;
    }
}
