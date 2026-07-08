package com.gateway.device.protocol.base.colorlight.standard.helper;

import com.gateway.device.protocol.base.colorlight.standard.ColorLightCommand;
import com.gateway.device.protocol.base.colorlight.standard.ColorLightVsnParser;
import com.gateway.device.protocol.base.colorlight.standard.model.MultipartPart;
import com.gateway.device.protocol.base.colorlight.standard.model.vsn.*;
import com.gateway.device.protocol.base.colorlight.standard.model.vsn.base.DisplayRect;
import com.gateway.device.protocol.base.colorlight.standard.model.vsn.base.FileSource;
import com.gateway.device.protocol.base.colorlight.standard.model.vsn.base.Information;
import com.gateway.device.protocol.base.colorlight.standard.model.vsn.enums.ItemType;
import com.gateway.device.protocol.base.colorlight.standard.model.vsn.enums.LoopType;
import com.gateway.device.protocol.base.colorlight.standard.model.vsn.enums.PathType;
import com.gateway.device.protocol.base.colorlight.standard.model.vsn.enums.ReserveMode;
import com.gateway.device.protocol.base.colorlight.standard.model.vsn.text.LogFont;
import com.gateway.device.protocol.common.constant.MediaType;
import com.gateway.device.protocol.common.file.MediaFileEntry;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * ColorLight VSN 节目便捷构建器。
 *
 * <p>提供常用 VSN 结构（单行文本、图片、视频等）的流式构建。</p>
 */
public final class ColorLightProgramBuilder {

    private ColorLightProgramBuilder() {
    }

    /**
     * 构建文本节目 VSN JSON（Type=5 多行文本，不含滚动）。
     */
    public static String buildText(String text, int x, int y, int width, int height) {
        return buildText(text, x, y, width, height, 16, "System", "0xFFFFFFFF", "0xFF000000");
    }

    /**
     * 构建文本节目 VSN JSON（完整参数）。
     */
    public static String buildText(String text, int x, int y, int width, int height,
                                   int fontSize, String fontName, String textColor, String bgColor) {
        VsnItem item = VsnItem.builder()
                .type(ItemType.MULTI_TEXT)
                .text(text)
                .backColor(bgColor)
                .textColor(textColor)
                .centeralAlign(1)
                .verticalAlign(1)
                .isScroll(ColorLightCommand.SCROLL_NO)
                .logFont(LogFont.builder()
                        .lfHeight(fontSize)
                        .lfFaceName(fontName)
                        .lfWeight(400)
                        .lfItalic(0)
                        .lfUnderline(0)
                        .build())
                .build();

        VsnRegion region = VsnRegion.builder()
                .rect(DisplayRect.builder().x(x).y(y).width(width).height(height).build())
                .items(Collections.singletonList(item))
                .build();

        VsnPrograms programs = VsnPrograms.builder()
                .programs(VsnPrograms.Programs.builder()
                        .program(VsnProgram.builder()
                                .information(Information.builder().width(width).height(height).build())
                                .pages(Collections.singletonList(VsnPage.builder()
                                        .regions(Collections.singletonList(region))
                                        .build()))
                                .build())
                        .build())
                .build();

        return ColorLightVsnParser.toJson(programs);
    }

    /**
     * 构建单行文本 VSN JSON。
     */
    public static String buildSingleText(String text, int x, int y, int width, int height) {
        return buildSingleText(text, x, y, width, height, 16, "System", "0xFFFFFFFF", "0xFF000000");
    }

    /**
     * 构建单行文本 VSN JSON（完整参数）。
     */
    public static String buildSingleText(String text, int x, int y, int width, int height,
                                         int fontSize, String fontName, String textColor, String bgColor) {
        VsnItem item = VsnItem.builder()
                .type(ItemType.SINGLE_TEXT)
                .text(text)
                .backColor(bgColor)
                .textColor(textColor)
                .isScroll(ColorLightCommand.SCROLL_NO)
                .logFont(LogFont.builder()
                        .lfHeight(fontSize)
                        .lfFaceName(fontName)
                        .lfWeight(400)
                        .lfItalic(0)
                        .lfUnderline(0)
                        .build())
                .build();

        VsnRegion region = VsnRegion.builder()
                .rect(DisplayRect.builder().x(x).y(y).width(width).height(height).build())
                .items(Collections.singletonList(item))
                .build();

        VsnPage page = VsnPage.builder()
                .regions(Collections.singletonList(region))
                .build();

        VsnPrograms programs = VsnPrograms.builder()
                .programs(VsnPrograms.Programs.builder()
                        .program(VsnProgram.builder()
                                .information(Information.builder().width(width).height(height).build())
                                .pages(Collections.singletonList(page))
                                .build())
                        .build())
                .build();

        return ColorLightVsnParser.toJson(programs);
    }

    /**
     * 构建滚动文本 VSN JSON。
     */
    public static String buildScrollText(String text, int x, int y, int width, int height,
                                         int fontSize, String fontName, int speed) {
        VsnItem item = VsnItem.builder()
                .type(ItemType.SINGLE_TEXT)
                .text(text)
                .isScroll(ColorLightCommand.SCROLL_YES)
                .speed(speed)
                .logFont(LogFont.builder()
                        .lfHeight(fontSize)
                        .lfFaceName(fontName)
                        .build())
                .build();

        VsnRegion region = VsnRegion.builder()
                .rect(DisplayRect.builder().x(x).y(y).width(width).height(height).build())
                .items(Collections.singletonList(item))
                .build();

        VsnPrograms programs = VsnPrograms.builder()
                .programs(VsnPrograms.Programs.builder()
                        .program(VsnProgram.builder()
                                .information(Information.builder().width(width).height(height).build())
                                .pages(Collections.singletonList(VsnPage.builder()
                                        .regions(Collections.singletonList(region))
                                        .build()))
                                .build())
                        .build())
                .build();

        return ColorLightVsnParser.toJson(programs);
    }

    /**
     * 构建图片节目 VSN JSON。
     */
    public static String buildImage(String filePath, int x, int y, int width, int height) {
        VsnItem item = VsnItem.builder()
                .type(ItemType.PICTURE)
                .volume(1.0f)
                .alpha(1.0f)
                .reserveAS(ReserveMode.FIT_XY)
                .fileSource(FileSource.builder()
                        .isRelative(PathType.RELATIVE)
                        .filePath(filePath)
                        .build())
                .build();

        VsnRegion region = VsnRegion.builder()
                .layer(1)
                .rect(DisplayRect.builder().x(x).y(y).width(width).height(height).build())
                .items(Collections.singletonList(item))
                .build();

        VsnPrograms programs = VsnPrograms.builder()
                .programs(VsnPrograms.Programs.builder()
                        .program(VsnProgram.builder()
                                .information(Information.builder().width(width).height(height).build())
                                .pages(Collections.singletonList(VsnPage.builder()
                                        .regions(Collections.singletonList(region))
                                        .build()))
                                .build())
                        .build())
                .build();

        return ColorLightVsnParser.toJson(programs);
    }

    /**
     * 清洗节目名称，仅保留中英文、数字、半角下划线(_)、半角连字符(-)，
     * 半角句号(.)替换为下划线(_)，结果最长 16 字符。
     *
     * @param fileName 原始文件名（可含扩展名）
     * @return 清洗后的节目名，由模板拼接 .vsn 得到完整节目名如 {@code BI001_jpg.vsn}
     */
    public static String sanitizeProgramName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        // 半角句号替换为下划线
        String name = fileName.replace('.', '_');
        // 仅保留：中文、英文、数字、下划线、连字符
        name = name.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9_-]", "");
        // 最长 16 字符
        if (name.length() > 16) {
            name = name.substring(0, 16);
        }
        return name;
    }

    /**
     * 构建 VSN FileSource 相对路径。
     *
     * <p>格式: {@code .\{programName}.files\{fileName}}
     *
     * @param programName 节目名（不含 .vsn 扩展名）
     * @param fileName    媒体文件名
     * @return VSN 相对路径，如 {@code .\BI001_jpg.files\BI001.jpg}
     */
    public static String buildFileSourcePath(String programName, String fileName) {
        return ".\\" + programName + ".files\\" + fileName;
    }

    /**
     * 构建 multipart/form-data 请求体。
     *
     * @param parts 表单部分列表
     * @return key=boundary, value=请求体二进制
     */
    public static Map.Entry<String, byte[]> buildMultipartBody(List<MultipartPart> parts) {
        String boundary = "-- boundary -- " + System.currentTimeMillis();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            for (MultipartPart part : parts) {
                StringBuilder header = new StringBuilder();
                header.append("--").append(boundary).append("\r\n");
                header.append("Content-Disposition: form-data; name=\"").append(part.getName()).append("\"");
                if (part.getFileName() != null && !part.getFileName().isEmpty()) {
                    header.append("; filename=\"").append(part.getFileName()).append("\"");
                }
                header.append("\r\n");
                header.append("\r\n");
                out.write(header.toString().getBytes(StandardCharsets.UTF_8));
                if (part.getData() != null) {
                    out.write(part.getData());
                }
                out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Failed to build multipart body", e);
        }
        return new AbstractMap.SimpleEntry<>(boundary, out.toByteArray());
    }

    /**
     * 构建多区域混合节目（图片+文本）。
     *
     * @param regions 区域列表
     * @param width   节目宽度
     * @param height  节目高度
     */
    public static String buildMixed(List<VsnRegion> regions, int width, int height) {
        VsnPage page = VsnPage.builder()
                .regions(regions)
                .build();

        VsnPrograms programs = VsnPrograms.builder()
                .programs(VsnPrograms.Programs.builder()
                        .program(VsnProgram.builder()
                                .information(Information.builder().width(width).height(height).build())
                                .pages(Collections.singletonList(page))
                                .build())
                        .build())
                .build();

        return ColorLightVsnParser.toJson(programs);
    }

    /**
     * 构建多页面节目 VSN JSON —— 每个媒体文件对应一个独立的节目页。
     *
     * <p>每个 {@link MediaFileEntry} 映射为一个 {@link VsnPage}，
     * 页面内包含一个全屏区域（{@link VsnRegion}），区域内包含一个素材项（{@link VsnItem}）。
     * 所有页面统一属于一个 {@link VsnProgram}。</p>
     *
     * @param entries     媒体文件条目列表（已按 order 升序排列）
     * @param programName 节目名（不含 .vsn 扩展名），用于构建 FileSource 相对路径
     * @param width       节目宽度（像素）
     * @param height      节目高度（像素）
     * @return VSN JSON 字符串
     */
    public static String buildMultiPage(List<MediaFileEntry> entries, String programName, int width, int height) {
        List<VsnPage> pages = new ArrayList<>(entries.size());
        for (MediaFileEntry entry : entries) {
            ItemType itemType = entry.getMediaType() == MediaType.VIDEO
                    ? ItemType.VIDEO
                    : ItemType.PICTURE;

            long duration = entry.getDuration() != null
                    ? entry.getDuration().longValue()
                    : 10000L;

            VsnItem item = VsnItem.builder()
                    .type(itemType)
                    .volume(1.0f)
                    .alpha(itemType == ItemType.PICTURE ? 1.0f : null)
                    .reserveAS(ReserveMode.FIT_XY)
                    .duration(duration)
                    .fileSource(FileSource.builder()
                            .isRelative(PathType.RELATIVE)
                            .filePath(buildFileSourcePath(programName, entry.getFileName()))
                            .build())
                    .build();

            VsnRegion region = VsnRegion.builder()
                    .layer(1)
                    .rect(DisplayRect.builder()
                            .x(0).y(0).width(width).height(height)
                            .build())
                    .items(Collections.singletonList(item))
                    .build();

            VsnPage page = VsnPage.builder()
                    .loopType(LoopType.AUTO)
                    .appointDuration(duration)
                    .regions(Collections.singletonList(region))
                    .build();

            pages.add(page);
        }

        VsnPrograms programs = VsnPrograms.builder()
                .programs(VsnPrograms.Programs.builder()
                        .program(VsnProgram.builder()
                                .information(Information.builder().width(width).height(height).build())
                                .pages(pages)
                                .build())
                        .build())
                .build();

        return ColorLightVsnParser.toJson(programs);
    }
}
