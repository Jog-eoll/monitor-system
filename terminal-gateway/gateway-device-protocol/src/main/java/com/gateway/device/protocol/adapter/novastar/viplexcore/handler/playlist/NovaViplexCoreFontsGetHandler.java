package com.gateway.device.protocol.adapter.novastar.viplexcore.handler.playlist;

import com.fasterxml.jackson.databind.JsonNode;
import com.gateway.device.protocol.adapter.novastar.viplexcore.handler.AbstractNovaViplexCoreHandler;
import com.gateway.device.protocol.base.novastar.viplexcore.SdkFunction;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexCoreChannel;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexErrorCode;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexResponse;
import com.gateway.device.protocol.base.novastar.viplexcore.builder.ViplexProgramPipeline;
import com.gateway.device.protocol.base.novastar.viplexcore.helper.ViplexCoreJsonBuilder;
import com.gateway.device.protocol.base.novastar.viplexcore.text.NovaViplexCoreTextStyle;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.EmptyParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 字体列表查询处理器 —— 获取终端当前已安装的字体列表。
 *
 * <p>SDK: {@code nvGetTerminalFontAsync}（请求: {@code {"sn":"xxx"}}）
 * <br>返回: {@code {"supportFonts":[{"name":"Arial","src":"system"},...]}}
 * <br>src 取值: {@code system}（系统字体）/ {@code custom}（用户上传）</p>
 */
@Slf4j
public class NovaViplexCoreFontsGetHandler extends AbstractNovaViplexCoreHandler<EmptyParams> {

    public NovaViplexCoreFontsGetHandler(
            ViplexCoreChannel channel,
            ViplexProgramPipeline pipeline,
            NovaViplexCoreTextStyle textStyle) {
        super(channel, pipeline, textStyle);
    }

    @Override
    public DeviceCapability<EmptyParams> capability() {
        return CommonDeviceCapability.FONTS_GET;
    }

    @Override
    public CommandResult execute(DeviceContext device, EmptyParams params) {
        String sn = device.getSn();
        if (StringUtils.isEmpty(sn)) {
            return CommandResult.failure(StandardErrorCode.INVALID_PARAM, "设备 SN 缺失");
        }

        log.info("查询字体列表 SN={}", sn);

        long start = System.currentTimeMillis();
        try {
            String json = ViplexCoreJsonBuilder.buildFontGetJson(sn);
            ViplexResponse resp = channel()
                    .execute(SdkFunction.NV_GET_TERMINAL_FONT_ASYNC, json, getTimeout());

            long cost = System.currentTimeMillis() - start;
            if (resp.isTimeout()) {
                return CommandResult.builder()
                        .success(false).code(StandardErrorCode.TIMEOUT)
                        .message("查询字体列表超时").costMillis(cost).build();
            }
            if (!resp.isSuccess()) {
                log.warn("查询字体列表失败 SN={} code={} data={}", sn, resp.getCode(), resp.getData());
                return CommandResult.builder()
                        .success(false).code(StandardErrorCode.PROTOCOL_ERROR)
                        .message(String.format("查询字体列表失败: %s", ViplexErrorCode.describe(resp.getCode())))
                        .costMillis(cost).build();
            }

            List<Map<String, Object>> fonts = parseFontList(resp.dataAsJson());
            log.info("查询字体列表成功 SN={} 共 {} 字体", sn, fonts.size());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("count", fonts.size());
            result.put("fonts", fonts);
            return CommandResult.builder()
                    .success(true).code(StandardErrorCode.SUCCESS)
                    .data(result).costMillis(cost).build();
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("查询字体列表异常 SN={}", sn, e);
            return CommandResult.builder()
                    .success(false).code(StandardErrorCode.SYSTEM_ERROR)
                    .message(String.format("查询字体列表异常: %s", e.getMessage()))
                    .costMillis(cost).build();
        }
    }

    /**
     * 解析 {@code nvGetTerminalFontAsync} 回调响应 JSON。
     *
     * <p>SDK 返回格式（exportviplexcoreasync.h）：
     * <pre>{@code
     * {
     *   "supportFonts": [
     *     { "name": "Arial",   "src": "system" },
     *     { "name": "Agency FB","src": "custom" },
     *     ...
     *   ]
     * }
     * }</pre>
     * src 取值: {@code system}（系统预装）/ {@code custom}（用户通过 nvUpdateFontAsync 上传）
     */
    private List<Map<String, Object>> parseFontList(JsonNode root) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (root == null) return result;

        JsonNode fontsNode = root.has("supportFonts") ? root.get("supportFonts") : root;
        if (!fontsNode.isArray()) return result;

        for (JsonNode fn : fontsNode) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", fn.has("name") ? fn.get("name").asText() : "");
            entry.put("src", fn.has("src") ? fn.get("src").asText() : "");
            result.add(entry);
        }
        return result;
    }
}
