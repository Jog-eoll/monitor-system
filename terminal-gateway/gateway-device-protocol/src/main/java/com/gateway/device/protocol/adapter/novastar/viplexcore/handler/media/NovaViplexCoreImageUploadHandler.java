package com.gateway.device.protocol.adapter.novastar.viplexcore.handler.media;

import com.gateway.device.protocol.adapter.novastar.viplexcore.handler.AbstractNovaViplexCoreHandler;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexCoreChannel;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexResponse;
import com.gateway.device.protocol.base.novastar.viplexcore.builder.ViplexProgramPipeline;
import com.gateway.device.protocol.base.novastar.viplexcore.helper.ViplexCoreFileHelper;
import com.gateway.device.protocol.base.novastar.viplexcore.text.NovaViplexCoreTextStyle;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.MediaType;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.MediaUploadParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 图片上传处理器 — 通过节目创建管线将图片上传到终端。
 *
 * <p>管线: GetFileMd5 → CreateProgram → SetPageProgram → MakeProgram → StartTransferProgram
 * <br>参数: data(byte[] 文件内容), fileName(文件名含扩展名), width(屏宽), height(屏高)</p>
 */
@Slf4j
public class NovaViplexCoreImageUploadHandler extends AbstractNovaViplexCoreHandler<MediaUploadParams> {

    public NovaViplexCoreImageUploadHandler(
            ViplexCoreChannel channel,
            ViplexProgramPipeline pipeline,
            NovaViplexCoreTextStyle textStyle) {
        super(channel, pipeline, textStyle);
    }

    @Override
    public DeviceCapability<MediaUploadParams> capability() {
        return CommonDeviceCapability.IMAGE_UPLOAD;
    }

    @Override
    public CommandResult execute(DeviceContext device, MediaUploadParams params) {
        String sn = device.getSn();
        if (StringUtils.isEmpty(sn)) {
            return CommandResult.failure(StandardErrorCode.INVALID_PARAM, "设备 SN 缺失");
        }

        byte[] data = params != null ? params.getData() : null;
        String fileName = params != null ? params.getFileName() : null;
        if (data == null || data.length == 0 || StringUtils.isEmpty(fileName)) {
            return CommandResult.failure(StandardErrorCode.INVALID_PARAM, "缺少 data 或 fileName 参数");
        }

        int width = params.resolveWidth(device.getWidth());
        int height = params.resolveHeight(device.getHeight());

        log.debug("[ImageUpload] SN={} file={} size={} {}x{}", sn, fileName, data.length, width, height);

        Path tmp;
        String outPutPath;
        try {
            tmp = ViplexCoreFileHelper.writeTempFile(data, fileName);
            outPutPath = pipeline().getProgramOutputDir();
        } catch (IOException e) {
            return CommandResult.failure(StandardErrorCode.SYSTEM_ERROR, String.format("创建临时文件失败: %s", e.getMessage()));
        }

        try {
            // 0. 获取文件 MD5
            String md5 = pipeline().getFileMd5(tmp.toString());
            if (md5 == null) {
                return CommandResult.failure(StandardErrorCode.PROTOCOL_ERROR, "获取文件 MD5 失败");
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

            // 2. 设置媒体页面
            ViplexResponse pageResp = pipeline().setMediaPage(
                    programId, 1, tmp.toString(), md5, fileName, MediaType.IMAGE, width, height, data.length);
            if (!pageResp.isSuccess()) {
                if (pageResp.isTimeout()) return CommandResult.timeout();
                return CommandResult.failure(StandardErrorCode.PROTOCOL_ERROR,
                        String.format("设置节目页面失败: %s", pageResp.getData()));
            }

            // 3. 制作节目
            ViplexResponse makeResp = pipeline().makeProgram(programId, outPutPath);
            if (!makeResp.isSuccess()) {
                if (makeResp.isTimeout()) return CommandResult.timeout();
                return CommandResult.failure(StandardErrorCode.PROTOCOL_ERROR,
                        String.format("制作节目失败: %s", makeResp.getData()));
            }

            // 4. 发送到终端
            Map<String, String> mediasPath = new LinkedHashMap<>();
            mediasPath.put(tmp.toString(), fileName);
            ViplexResponse transferResp = pipeline().transferProgram(sn, programId, outPutPath,
                    mediasPath);
            if (transferResp.isTimeout()) return CommandResult.timeout();
            if (!transferResp.isSuccess()) {
                return CommandResult.failure(StandardErrorCode.PROTOCOL_ERROR,
                        String.format("发送节目失败: %s", transferResp.getData()));
            }

            return CommandResult.success();
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
            }
        }
    }
}
