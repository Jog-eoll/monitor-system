package com.gateway.device.protocol.adapter.jetfileii.standard.handler.playlist;

import com.gateway.device.protocol.adapter.jetfileii.standard.handler.AbstractJetFileIIHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.base.jetfileii.standard.helper.JetFileIIMessaging;
import com.gateway.device.protocol.base.jetfileii.standard.text.FntParser;
import com.gateway.device.protocol.base.jetfileii.standard.text.FontListFile;
import com.gateway.device.protocol.base.jetfileii.standard.text.constant.JetFileIIFont;
import com.gateway.device.protocol.base.jetfileii.standard.transfer.FileTransfer;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.common.font.FontFileEntry;
import com.gateway.device.protocol.common.font.FontFileScanner;
import com.gateway.device.protocol.common.font.FontFileType;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.FontSyncParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 字体同步处理器 —— 全量上传字体到目标设备。
 *
 * <p>从本地字库目录加载 {@link JetFileIIFont} 对应的 .fnt 文件，
 * 写入设备 C:\FONT\，全量覆盖 FONTLIST.LST。
 * 同步执行，与 Handler 请求-响应模式一致。</p>
 *
 * <p>字体来源优先级：
 * <ol>
 *   <li>命令参数显式指定 → 只同步指定字体</li>
 *   <li>参数为空 → 递归扫描 {@code fontLocalPath} 自动发现所有已知字库文件</li>
 * </ol></p>
 */
@Slf4j
public class JetFileIIFontSyncHandler extends AbstractJetFileIIHandler<FontSyncParams> {

    private final String fontLocalPath;

    public JetFileIIFontSyncHandler(JetFileIIMessaging messaging,
                                    DeviceTransport transport,
                                    String fontLocalPath) {
        super(messaging, transport);
        this.fontLocalPath = fontLocalPath;
    }

    // ── 工具方法 ──────────────────────────────────────────

    private static int simpleChecksum(byte[] data) {
        int sum = 0;
        for (byte b : data) sum += (b & 0xFF);
        return sum;
    }

    private static byte encodingType(int width) {
        if (width >= 25) return 4;
        if (width >= 18) return 3;
        if (width >= 10) return 2;
        return 1;
    }

    @Override
    public DeviceCapability<FontSyncParams> capability() {
        return CommonDeviceCapability.FONTS_SYNC;
    }

    @Override
    public CommandResult execute(DeviceContext device, FontSyncParams params) {
        Map<JetFileIIFont, Path> fontMap;
        if (params != null && CollectionUtils.isNotEmpty(params.getJetFileIIFonts())) {
            fontMap = resolveFontPaths(params.getJetFileIIFonts());
        } else {
            fontMap = discoverFonts();
        }

        if (fontMap.isEmpty()) {
            return CommandResult.failure(StandardErrorCode.INVALID_PARAM,
                    String.format("无可同步字体 (fontLocalPath=%s)", fontLocalPath));
        }

        try {
            FileTransfer ft = createFileTransfer(device);
            List<FontListFile.FontEntry> entries = new ArrayList<>();
            int uploaded = 0;

            for (Map.Entry<JetFileIIFont, Path> entry : fontMap.entrySet()) {
                JetFileIIFont font = entry.getKey();
                String deviceName = font.getDevicePath();
                if (deviceName == null) {
                    log.debug("[{}] 字体 {} 无独立字库文件，跳过", device.getIp(), font.name());
                    continue;
                }

                byte[] fontData = loadFontFile(entry.getValue());
                if (fontData == null) {
                    log.warn("[{}] 本地字库未找到: {} ({})", device.getIp(), deviceName, font.name());
                    continue;
                }

                ft.writeFontFile(deviceName, fontData);
                FontListFile.FontEntry fontEntry = buildEntry(font, fontData);
                entries.add(fontEntry);
                uploaded++;

                log.debug("[{}] 已上传字体: {} ({}B, {}×{})",
                        device.getIp(), deviceName, fontData.length,
                        fontEntry.getWidth(), fontEntry.getHeight());
            }

            if (uploaded > 0) {
                ft.writeFontList(new FontListFile(entries));
                log.info("[{}] FONTLIST.LST 已全量覆盖 ({} 字体)", device.getIp(), entries.size());
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("uploaded", uploaded);
            return CommandResult.success(result);
        } catch (Exception e) {
            log.error("[{}] 字体同步失败: {}", device.getIp(), e.getMessage(), e);
            return CommandResult.failure(StandardErrorCode.SYSTEM_ERROR, e.getMessage());
        }
    }

    /**
     * 自动发现本地字库文件 → 厂商枚举匹配，一次扫描建立字体→路径映射。
     *
     * <p>1. {@link FontFileScanner} 递归扫描 FNT 文件（公共组件）</p>
     * <p>2. 文件名与 {@link JetFileIIFont#getDevicePath()} 大小写无关匹配（厂商逻辑）</p>
     *
     * @return 字体→文件路径映射
     */
    private Map<JetFileIIFont, Path> discoverFonts() {
        Path dir = Paths.get(fontLocalPath);
        List<FontFileEntry> entries = FontFileScanner.scan(dir, FontFileType.FNT);
        if (entries.isEmpty()) {
            log.info("字库目录中无 FNT 文件: {}", fontLocalPath);
            return Collections.emptyMap();
        }

        Map<JetFileIIFont, Path> fontMap = new LinkedHashMap<>();
        for (FontFileEntry entry : entries) {
            for (JetFileIIFont font : JetFileIIFont.values()) {
                if (font.getDevicePath() != null
                        && font.getDevicePath().equalsIgnoreCase(entry.getFileName())) {
                    fontMap.putIfAbsent(font, entry.getFullPath());
                    break;
                }
            }
        }

        log.info("字库自动发现完成: {}/{} 字体匹配 (目录: {})",
                fontMap.size(), entries.size(), fontLocalPath);
        return fontMap;
    }

    /**
     * 显式指定字体时，扫描定位对应的文件路径。
     */
    private Map<JetFileIIFont, Path> resolveFontPaths(List<JetFileIIFont> fonts) {
        Path dir = Paths.get(fontLocalPath);
        List<FontFileEntry> entries = FontFileScanner.scan(dir, FontFileType.FNT);

        Map<JetFileIIFont, Path> fontMap = new LinkedHashMap<>();
        for (JetFileIIFont font : fonts) {
            if (font.getDevicePath() == null) continue;
            entries.stream()
                    .filter(e -> e.getFileName().equalsIgnoreCase(font.getDevicePath()))
                    .findFirst()
                    .ifPresent(e -> fontMap.put(font, e.getFullPath()));
        }
        return fontMap;
    }

    /**
     * 从已知路径加载字体文件内容。
     */
    private byte[] loadFontFile(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            log.warn("读取字体文件失败: {}", file, e);
            return null;
        }
    }

    private FontListFile.FontEntry buildEntry(JetFileIIFont font, byte[] fontData) {
        int[] wh = FntParser.dimensions(fontData);
        return FontListFile.FontEntry.builder()
                .fileName(font.getDevicePath())
                .fontCode(font.getCode())
                .fileSize(fontData.length)
                .checksum(simpleChecksum(fontData))
                .width(wh[0])
                .height(wh[1])
                .attr1(encodingType(wh[0]))
                .attr2((byte) wh[0])
                .build();
    }
}
