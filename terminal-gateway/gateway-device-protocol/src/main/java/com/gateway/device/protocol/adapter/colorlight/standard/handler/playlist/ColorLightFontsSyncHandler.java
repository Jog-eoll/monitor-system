package com.gateway.device.protocol.adapter.colorlight.standard.handler.playlist;

import com.gateway.device.protocol.adapter.colorlight.standard.ColorLightCredentialStore;
import com.gateway.device.protocol.adapter.colorlight.standard.handler.AbstractColorLightHttpHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.api.ProtocolCodec;
import com.gateway.device.protocol.base.colorlight.standard.ColorLightApi;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpRequest;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpResponse;
import com.gateway.device.protocol.base.colorlight.standard.helper.ColorLightProgramBuilder;
import com.gateway.device.protocol.base.colorlight.standard.model.MultipartPart;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.common.font.FontFileEntry;
import com.gateway.device.protocol.common.font.FontFileScanner;
import com.gateway.device.protocol.common.font.FontFileType;
import com.gateway.device.protocol.common.font.GeneralFont;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.FontSyncParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ColorLight 字体同步 —— POST /api/fonts（multipart/form-data）。
 *
 * <p>ColorLight 仅支持 TTF 格式，通过 HTTP multipart 逐文件上传。
 * 字体来源：{@link FontSyncParams#getFonts()} 显式指定（仅 TTF），
 * 或自动扫描 {@code fontLocalPath} 下所有 TTF 文件并通过 {@link GeneralFont#ofFile(String)} 匹配。</p>
 */
@Slf4j
public class ColorLightFontsSyncHandler extends AbstractColorLightHttpHandler<FontSyncParams> {

    /**
     * 字体上传超时，TTF 文件需充足传输时间
     */
    private static final Duration FONT_SYNC_TIMEOUT = Duration.ofSeconds(120);
    private final String fontLocalPath;

    public ColorLightFontsSyncHandler(DeviceTransport transport,
                                      ColorLightCredentialStore credentialStore,
                                      ProtocolCodec<ColorLightHttpRequest, ColorLightHttpResponse> codec,
                                      String fontLocalPath) {
        super(transport, credentialStore, codec);
        this.fontLocalPath = fontLocalPath;
    }

    @Override
    protected Duration getTimeout() {
        return FONT_SYNC_TIMEOUT;
    }

    @Override
    public DeviceCapability<FontSyncParams> capability() {
        return CommonDeviceCapability.FONTS_SYNC;
    }

    @Override
    public CommandResult execute(DeviceContext device, FontSyncParams params) {
        if (fontLocalPath == null || fontLocalPath.isEmpty()) {
            return CommandResult.failure(StandardErrorCode.INVALID_PARAM, "fontLocalPath 未配置");
        }

        // 字体来源：自动发现 TTF → 枚举匹配，显式参数则过滤 TTF 类型
        Map<GeneralFont, Path> fontFiles;
        if (params != null && CollectionUtils.isNotEmpty(params.getFonts())) {
            // 仅处理 TTF 类型
            List<GeneralFont> ttfFonts = params.getFonts().stream()
                    .filter(f -> f.getFontFileType() == FontFileType.TTF)
                    .collect(Collectors.toList());
            fontFiles = resolveRequestedFonts(ttfFonts);
            log.info("ColorLight 字体同步: {}/{} 个指定字体已匹配 (fontLocalPath={})",
                    fontFiles.size(), ttfFonts.size(), fontLocalPath);
        } else {
            fontFiles = discoverFonts();
        }

        if (fontFiles.isEmpty()) {
            return CommandResult.failure(StandardErrorCode.INVALID_PARAM,
                    String.format("无可同步字体 (fontLocalPath=%s)", fontLocalPath));
        }

        log.info("ColorLight 字体同步开始: IP={} path={} fonts=[{}] 共 {} 字体",
                device.getIp(), fontLocalPath,
                fontFiles.keySet().stream().map(GeneralFont::getFamily).collect(Collectors.joining(",")),
                fontFiles.size());

        long start = System.currentTimeMillis();
        int uploaded = 0;
        for (Map.Entry<GeneralFont, Path> entry : fontFiles.entrySet()) {
            GeneralFont font = entry.getKey();
            Path filePath = entry.getValue();
            try {
                byte[] fontData = Files.readAllBytes(filePath);
                if (fontData.length == 0) {
                    log.warn("ColorLight 字体文件为空: {}", filePath);
                    continue;
                }

                Map.Entry<String, byte[]> multipart = ColorLightProgramBuilder.buildMultipartBody(
                        Collections.singletonList(MultipartPart.builder()
                                .name("file")
                                .fileName(font.getFileName())
                                .data(fontData)
                                .build()));

                ColorLightHttpResponse resp = postMultipart(device,
                        ColorLightApi.FONTS_SYNC.path(),
                        multipart.getValue(), multipart.getKey());

                if (resp == null || resp.getStatusCode() != HttpURLConnection.HTTP_OK) {
                    long cost = System.currentTimeMillis() - start;
                    return CommandResult.builder()
                            .success(false).code(StandardErrorCode.PROTOCOL_ERROR)
                            .message(String.format("字体上传失败: %s (status=%d)", font.getFamily(),
                                    resp != null ? resp.getStatusCode() : -1))
                            .costMillis(cost).build();
                }
                uploaded++;
                log.debug("ColorLight 字体已上传: {} ({}B)", font.getFileName(), fontData.length);
            } catch (Exception e) {
                long cost = System.currentTimeMillis() - start;
                log.error("ColorLight 字体上传异常: {}", font.getFileName(), e);
                return CommandResult.builder()
                        .success(false).code(StandardErrorCode.SYSTEM_ERROR)
                        .message(String.format("字体上传异常: %s - %s", font.getFamily(), e.getMessage()))
                        .costMillis(cost).build();
            }
        }

        long cost = System.currentTimeMillis() - start;
        log.info("ColorLight 字体同步完成: IP={} 共 {} 字体", device.getIp(), uploaded);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uploaded", uploaded);
        return CommandResult.builder()
                .success(true).code(StandardErrorCode.SUCCESS)
                .data(result).costMillis(cost).build();
    }

    // ── 字体发现 ──

    /**
     * 自动发现 TTF 字体文件 → 枚举匹配。
     * 使用 {@link FontFileScanner} 递归扫描 TTF，通过 {@link GeneralFont#ofFile(String)} 匹配已知枚举。
     */
    private Map<GeneralFont, Path> discoverFonts() {
        Path dir = Paths.get(fontLocalPath);
        List<FontFileEntry> entries = FontFileScanner.scan(dir, FontFileType.TTF);
        if (entries.isEmpty()) {
            log.info("TTF 目录中无字体文件: {}", fontLocalPath);
            return Collections.emptyMap();
        }

        Map<GeneralFont, Path> result = new LinkedHashMap<>();
        for (FontFileEntry entry : entries) {
            GeneralFont gf = GeneralFont.ofFile(entry.getFileName());
            if (gf != null) {
                result.putIfAbsent(gf, entry.getFullPath());
            }
        }
        log.info("TTF 自动发现完成: {}/{} 文件匹配", result.size(), entries.size());
        return result;
    }

    /**
     * 根据显式指定的字体列表，扫描定位对应文件路径。
     */
    private Map<GeneralFont, Path> resolveRequestedFonts(List<GeneralFont> requested) {
        Path dir = Paths.get(fontLocalPath);
        List<FontFileEntry> entries = FontFileScanner.scan(dir, FontFileType.TTF);
        if (entries.isEmpty()) {
            log.info("TTF 目录中无字体文件: {}", fontLocalPath);
            return Collections.emptyMap();
        }

        Set<String> requestedNames = requested.stream()
                .map(GeneralFont::getFileName)
                .collect(Collectors.toSet());

        Map<GeneralFont, Path> result = new LinkedHashMap<>();
        for (FontFileEntry entry : entries) {
            if (!requestedNames.contains(entry.getFileName())) continue;
            GeneralFont gf = GeneralFont.ofFile(entry.getFileName());
            if (gf != null) {
                result.putIfAbsent(gf, entry.getFullPath());
            }
        }
        return result;
    }
}
