package com.gateway.device.protocol.adapter.novastar.viplexcore.handler.playlist;

import com.gateway.device.protocol.adapter.novastar.viplexcore.handler.AbstractNovaViplexCoreHandler;
import com.gateway.device.protocol.base.novastar.viplexcore.SdkFunction;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexCoreChannel;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexErrorCode;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexResponse;
import com.gateway.device.protocol.base.novastar.viplexcore.builder.ViplexProgramPipeline;
import com.gateway.device.protocol.base.novastar.viplexcore.font.NovaStarFontInfo;
import com.gateway.device.protocol.base.novastar.viplexcore.helper.ViplexCoreJsonBuilder;
import com.gateway.device.protocol.base.novastar.viplexcore.text.NovaViplexCoreTextStyle;
import com.gateway.device.protocol.common.GatewayTimeoutConstants;
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
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 字体同步处理器 —— 上传 TrueType 字体到终端。
 *
 * <p>SDK: {@code nvUpdateFontAsync}
 * <br>参数: {@link FontSyncParams#getFonts()}（{@link GeneralFont} 枚举列表，仅 TTF）
 * <br>参数为空时递归扫描 {@code fontLocalPath} 自动发现。</p>
 *
 * <p>SDK 内部读取 {@code fontLocalPath} 下的 .ttf 文件并传输到终端，
 * 无需本 Handler 自行读取文件。</p>
 */
@Slf4j
public class NovaViplexCoreFontsSyncHandler extends AbstractNovaViplexCoreHandler<FontSyncParams> {

    /**
     * 字体 FTP 上传超时，TTF 文件需充足传输时间
     */
    private final String fontLocalPath;

    public NovaViplexCoreFontsSyncHandler(
            ViplexCoreChannel channel,
            ViplexProgramPipeline pipeline,
            NovaViplexCoreTextStyle textStyle,
            String fontLocalPath) {
        super(channel, pipeline, textStyle);
        this.fontLocalPath = fontLocalPath;
    }

    @Override
    protected Duration getTimeout() {
        return Duration.ofMillis(GatewayTimeoutConstants.DEVICE_FONT_SYNC_MS);
    }

    @Override
    public DeviceCapability<FontSyncParams> capability() {
        return CommonDeviceCapability.FONTS_SYNC;
    }

    @Override
    public CommandResult execute(DeviceContext device, FontSyncParams params) {
        String sn = device.getSn();
        if (StringUtils.isEmpty(sn)) {
            return CommandResult.failure(StandardErrorCode.INVALID_PARAM, "设备 SN 缺失");
        }

        // 字体来源：自动发现（获取实际文件目录 + 纯文件名），显式参数则过滤 TTF 并匹配
        FontDiscovery discovery = discoverTtfFonts(fontLocalPath);
        String effectiveFontPath = discovery.effectivePath;
        List<NovaStarFontInfo> fontInfos;
        if (params != null && CollectionUtils.isNotEmpty(params.getFonts())) {
            // 仅处理 TTF 类型
            List<GeneralFont> ttfFonts = params.getFonts().stream()
                    .filter(f -> f.getFontFileType() == FontFileType.TTF)
                    .collect(Collectors.toList());
            Set<String> requestedNames = ttfFonts.stream()
                    .map(GeneralFont::getFamily)
                    .collect(Collectors.toSet());
            fontInfos = discovery.fonts.stream()
                    .filter(fi -> requestedNames.contains(fi.getName()))
                    .collect(Collectors.toList());
            log.info("字体同步: {}/{} 个指定字体已匹配 (effectivePath={})",
                    fontInfos.size(), ttfFonts.size(), effectiveFontPath);
        } else {
            fontInfos = discovery.fonts;
        }

        if (CollectionUtils.isEmpty(fontInfos)) {
            return CommandResult.failure(StandardErrorCode.INVALID_PARAM,
                    String.format("无可同步字体 (fontLocalPath=%s)", fontLocalPath));
        }

        log.info("字体同步开始: SN={} path={} fonts=[{}] 共 {} 字体",
                sn, effectiveFontPath,
                fontInfos.stream().map(NovaStarFontInfo::getName).collect(Collectors.joining(",")),
                fontInfos.size());

        long start = System.currentTimeMillis();
        try {
            String json = ViplexCoreJsonBuilder.buildFontUpdateJson(sn, effectiveFontPath, fontInfos);
            log.debug("[nvUpdateFont] JSON length: {}", json.length());

            ViplexResponse resp = channel()
                    .execute(SdkFunction.NV_UPDATE_FONT_ASYNC, json, getTimeout());

            long cost = System.currentTimeMillis() - start;
            if (resp.isTimeout()) {
                return CommandResult.builder()
                        .success(false).code(StandardErrorCode.TIMEOUT)
                        .message("字体同步超时").costMillis(cost).build();
            }
            if (!resp.isSuccess()) {
                log.warn("字体同步失败 SN={} code={} data={}", sn, resp.getCode(), resp.getData());
                return CommandResult.builder()
                        .success(false).code(StandardErrorCode.PROTOCOL_ERROR)
                        .message(String.format("字体同步失败: %s", ViplexErrorCode.describe(resp.getCode())))
                        .costMillis(cost).build();
            }

            log.info("字体同步成功 SN={} 共 {} 字体", sn, fontInfos.size());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("uploaded", fontInfos.size());
            return CommandResult.builder()
                    .success(true).code(StandardErrorCode.SUCCESS)
                    .data(result).costMillis(cost).build();
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("字体同步异常 SN={}", sn, e);
            return CommandResult.builder()
                    .success(false).code(StandardErrorCode.SYSTEM_ERROR)
                    .message(String.format("字体同步异常: %s", e.getMessage()))
                    .costMillis(cost).build();
        }
    }

    /**
     * 自动发现 TTF 字体 → 枚举精确匹配。
     *
     * <p>1. {@link FontFileScanner} 递归扫描 TTF 文件，返回 {@link FontFileEntry} 列表</p>
     * <p>2. 文件名通过 {@link GeneralFont#ofFile(String)} 匹配已知枚举</p>
     * <p>3. 计算所有匹配文件的公共父目录作为 {@code effectivePath}，
     * 传给 SDK 的 {@code localFontPath}，{@code file} 只用纯文件名</p>
     */
    private FontDiscovery discoverTtfFonts(String configFontPath) {
        Path dir = Paths.get(configFontPath);
        List<FontFileEntry> entries = FontFileScanner.scan(dir, FontFileType.TTF);
        if (entries.isEmpty()) {
            log.info("TTF 目录中无字体文件: {}", configFontPath);
            return new FontDiscovery(Collections.emptyList(), configFontPath);
        }

        // 匹配枚举，同时按文件所在父目录分组
        Map<Path, List<NovaStarFontInfo>> byParent = new LinkedHashMap<>();
        for (FontFileEntry entry : entries) {
            GeneralFont gf = GeneralFont.ofFile(entry.getFileName());
            if (gf == null) continue;
            Path parent = entry.getFullPath().getParent();
            byParent.computeIfAbsent(parent, k -> new ArrayList<>())
                    .add(gf.toFontInfo());
        }

        List<NovaStarFontInfo> result = byParent.values().stream()
                .flatMap(List::stream).collect(Collectors.toList());

        // 有效的 localFontPath：所有匹配文件的公共父目录
        String effectivePath;
        if (byParent.size() == 1) {
            effectivePath = byParent.keySet().iterator().next().toString();
        } else {
            // 多个父目录，保留配置路径（SDK 可能无法处理分散的字体）
            effectivePath = configFontPath;
            log.warn("TTF 字体分散在多个目录，使用配置路径: {}", configFontPath);
        }

        log.info("TTF 自动发现完成: {}/{} 文件匹配, effectivePath={}",
                result.size(), entries.size(), effectivePath);
        return new FontDiscovery(result, effectivePath);
    }

    /**
     * 字体发现结果：字体列表 + TTF 文件实际所在目录（用于传给 SDK）。
     */
    private static class FontDiscovery {
        final List<NovaStarFontInfo> fonts;
        final String effectivePath;

        FontDiscovery(List<NovaStarFontInfo> fonts, String effectivePath) {
            this.fonts = fonts;
            this.effectivePath = effectivePath;
        }
    }
}
