package com.gateway.device.protocol.adapter.novastar.viplexcore.handler.media;

import com.gateway.device.protocol.adapter.novastar.viplexcore.handler.AbstractNovaViplexCoreHandler;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexCoreChannel;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexResponse;
import com.gateway.device.protocol.base.novastar.viplexcore.builder.ViplexProgramPipeline;
import com.gateway.device.protocol.base.novastar.viplexcore.text.NovaViplexCoreTextStyle;
import com.gateway.device.protocol.common.JsonCustomMapper;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.TextUploadParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Collections;

/**
 * 文本上传处理器 — 通过节目创建管线将文本内容上传到终端。
 *
 * <p>管线: CreateProgram → SetPageProgram → MakeProgram → StartTransferProgram
 * <br>参数: text(文本内容), width(屏宽), height(屏高)
 * <br>可选: textStyle(NovaViplexCoreTextStyle 或 Map，非 null 字段覆盖 yml 默认值)</p>
 */
@Slf4j
public class NovaViplexCoreTextUploadHandler extends AbstractNovaViplexCoreHandler<TextUploadParams> {

    public NovaViplexCoreTextUploadHandler(
            ViplexCoreChannel channel,
            ViplexProgramPipeline pipeline,
            NovaViplexCoreTextStyle textStyle) {
        super(channel, pipeline, textStyle);
    }

    @Override
    public DeviceCapability<TextUploadParams> capability() {
        return CommonDeviceCapability.TEXT_UPLOAD;
    }

    @Override
    public CommandResult execute(DeviceContext device, TextUploadParams params) {
        String sn = device.getSn();
        if (StringUtils.isEmpty(sn)) {
            return CommandResult.failure(StandardErrorCode.INVALID_PARAM, "设备 SN 缺失");
        }

        String text = params != null ? params.getText() : null;
        if (StringUtils.isEmpty(text)) {
            return CommandResult.failure(StandardErrorCode.INVALID_PARAM, "缺少 text 参数");
        }

        int width = params.resolveWidth(device.getWidth());
        int height = params.resolveHeight(device.getHeight());

        log.debug("[TextUpload] SN={} textLen={} {}x{}", sn, text.length(), width, height);

        String outPutPath;
        try {
            outPutPath = pipeline().getProgramOutputDir();
        } catch (IOException e) {
            return CommandResult.failure(StandardErrorCode.SYSTEM_ERROR, String.format("创建输出目录失败: %s", e.getMessage()));
        }

        // 1. 创建节目
        ViplexResponse createResp = pipeline().createProgram(width, height);
        if (!createResp.isSuccess()) {
            if (createResp.isTimeout()) return CommandResult.timeout();
            return CommandResult.failure(StandardErrorCode.PROTOCOL_ERROR,
                    String.format("创建节目失败: %s", createResp.getData()));
        }
        String programId = pipeline().parseProgramId(createResp);
        if (programId == null) {
            return CommandResult.failure(StandardErrorCode.PROTOCOL_ERROR,
                    String.format("创建节目响应缺少 programID: %s", createResp.getData()));
        }

        // 2. 设置文本页面（支持 params.textStyle 覆盖）
        NovaViplexCoreTextStyle effectiveStyle = resolveTextStyle(params);
        ViplexResponse pageResp =
                pipeline().setTextPage(programId, 1, text, width, height, effectiveStyle);
        if (!pageResp.isSuccess()) {
            if (pageResp.isTimeout()) return CommandResult.timeout();
            return CommandResult.failure(StandardErrorCode.PROTOCOL_ERROR,
                    String.format("设置文本页面失败: %s", pageResp.getData()));
        }

        // 3. 制作节目
        ViplexResponse makeResp = pipeline().makeProgram(programId, outPutPath);
        if (!makeResp.isSuccess()) {
            if (makeResp.isTimeout()) return CommandResult.timeout();
            return CommandResult.failure(StandardErrorCode.PROTOCOL_ERROR,
                    String.format("制作节目失败: %s", makeResp.getData()));
        }

        // 4. 发送到终端
        ViplexResponse transferResp = pipeline().transferProgram(sn, programId, outPutPath,
                Collections.emptyMap());
        if (transferResp.isTimeout()) return CommandResult.timeout();
        if (!transferResp.isSuccess()) {
            return CommandResult.failure(StandardErrorCode.PROTOCOL_ERROR,
                    String.format("发送节目失败: %s", transferResp.getData()));
        }

        return CommandResult.success();
    }

    /**
     * 从 params 解析文本样式覆盖。支持 {@link NovaViplexCoreTextStyle} 对象或 Map。
     * 非 null 字段覆盖默认值，返回合并后的样式。
     */
    private NovaViplexCoreTextStyle resolveTextStyle(TextUploadParams params) {
        if (params == null) return textStyle();
        Object tsObj = params.getTextStyle();
        if (tsObj == null) return textStyle();
        if (tsObj instanceof NovaViplexCoreTextStyle) {
            return textStyle().merge((NovaViplexCoreTextStyle) tsObj);
        }
        try {
            NovaViplexCoreTextStyle override = JsonCustomMapper.get()
                    .convertValue(tsObj, NovaViplexCoreTextStyle.class);
            return textStyle().merge(override);
        } catch (Exception e) {
            log.warn("textStyle 参数转换失败，使用默认配置: {}", e.getMessage());
            return textStyle();
        }
    }
}
