package com.gateway.device.protocol.base.jetfileii.standard.text;

import com.gateway.device.protocol.base.jetfileii.standard.text.constant.FontColor;
import com.gateway.device.protocol.base.jetfileii.standard.text.constant.JetFileIIFont;
import com.gateway.device.protocol.common.constant.ProtocolConstant;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;

/**
 * 自定义文本 → NmgTextFile 构建器。
 *
 * <p>按行拆分文本，每行可独立设定字体颜色和字体大小。
 * 不做中/英文自动分类——颜色指派由调用方决定。</p>
 *
 * <pre>{@code
 *   // 每行独立颜色
 *   NmgTextFile nmg = NmgTextBuilder.builder()
 *           .text("勤能补拙\nLINE ONE\n科技\nScience")
 *           .controlPrefix(prefix)
 *           .lineColors(chineseBlue, englishRed, chineseBlue, englishRed)
 *           .lineFonts(font16x16, font16x9, font16x16, font16x9)
 *           .build();
 * }</pre>
 */
public final class NmgTextBuilder {


    private String text;
    private byte[] controlPrefix;
    /**
     * 每行字体颜色字节 (预定义1B / 自定义BGR 4B)
     */
    private byte[][] lineColors;
    /**
     * 每行字体代码
     */
    private byte[] lineFonts;
    /**
     * 文本段 (按字符/词组精细控制颜色)
     */
    private NmgTextSegment[] segments;

    private NmgTextBuilder() {
    }

    public static NmgTextBuilder builder() {
        return new NmgTextBuilder();
    }

    // ── 配置方法 ────────────────────────────────────

    /**
     * 快捷方法: 直接构建并返回纯文本体
     */
    public static byte[] buildToBytes(String text) {
        return builder().text(text).build().toBytes();
    }

    /**
     * 设置显示文本 (支持 \n 换行, GB18030 统一编码)
     */
    public NmgTextBuilder text(String text) {
        this.text = text;
        return this;
    }

    /**
     * 设置自定义控制前缀 (默认使用 NmgControlBuilder.buildDefault())
     */
    public NmgTextBuilder controlPrefix(byte[] prefix) {
        this.controlPrefix = prefix;
        return this;
    }

    /**
     * 按文本段精细控制颜色 (覆盖 text/lineColors)
     */
    public NmgTextBuilder segments(NmgTextSegment... segments) {
        this.segments = segments;
        return this;
    }

    /**
     * 设置每行字体颜色字节序列 (与行数对应)
     */
    public NmgTextBuilder lineColors(byte[]... colors) {
        this.lineColors = colors;
        return this;
    }

    /**
     * 每行字体颜色 (枚举便捷方法)
     */
    public NmgTextBuilder lineColors(FontColor... colors) {
        this.lineColors = new byte[colors.length][];
        for (int i = 0; i < colors.length; i++)
            this.lineColors[i] = new byte[]{colors[i].getCode()};
        return this;
    }

    /**
     * 设置每行字体代码 (与行数对应)
     */
    public NmgTextBuilder lineFonts(byte... fonts) {
        this.lineFonts = fonts;
        return this;
    }

    /**
     * 每行字体 (枚举便捷方法)
     */
    public NmgTextBuilder lineFonts(JetFileIIFont... fonts) {
        this.lineFonts = new byte[fonts.length];
        for (int i = 0; i < fonts.length; i++)
            this.lineFonts[i] = (byte) fonts[i].getCode();
        return this;
    }

    // ── 构建 ────────────────────────────────────────

    /**
     * 便捷: 直接用 NmgControlBuilder 设置控制前缀
     */
    public NmgTextBuilder controlPrefix(NmgControlBuilder cb) {
        this.controlPrefix = cb.build();
        return this;
    }

    /**
     * 构建 NmgTextFile
     */
    public NmgTextFile build() {
        byte[] prefix = controlPrefix != null
                ? controlPrefix : NmgControlBuilder.buildDefault();

        byte[] displayText = buildDisplayText(text != null ? text : "");

        return NmgTextFile.builder()
                .controlPrefix(prefix)
                .displayText(displayText)
                .note(NmgTextFile.buildDefaultNote())
                .build();
    }

    // ── 内部逻辑 ────────────────────────────────────

    private byte[] buildDisplayText(String text) {
        // segments 优先
        if (segments != null && segments.length > 0) {
            return buildFromSegments();
        }

        if (text.isEmpty()) return new byte[]{ProtocolConstant.CR};

        String[] lines = text.split("\n", -1);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        boolean hasColors = lineColors != null;
        byte[] prevColor = null;
        byte prevFont = 0;

        for (int i = 0; i < lines.length; i++) {
            if (hasColors) {
                byte[] color = getColor(i);
                byte font = getFont(i);
                if (prevColor == null || !Arrays.equals(color, prevColor) || font != prevFont) {
                    buf.write(0x1C);
                    buf.write(color, 0, color.length);
                    buf.write(0x1A);
                    buf.write(font);
                    prevColor = color;
                    prevFont = font;
                }
            }

            if (!lines[i].isEmpty()) {
                byte[] encoded = lines[i].getBytes(ProtocolConstant.GB18030);
                try {
                    buf.write(encoded);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            buf.write(ProtocolConstant.CR);
        }

        try {
            buf.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return buf.toByteArray();
    }

    /**
     * 从 segments 构建显示文本 (逐段编码, 颜色/字体变化时插入切换)
     */
    private byte[] buildFromSegments() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] prevColor = null;
        byte prevFont = 0;

        for (NmgTextSegment seg : segments) {
            // \n → CR (0x0D)
            String t = seg.text.replace("\n", "\r");

            // 字体切换
            if (seg.fontColor != null && seg.fontCode != 0) {
                if (prevColor == null || !Arrays.equals(seg.fontColor, prevColor)
                        || seg.fontCode != prevFont) {
                    buf.write(0x1C);
                    buf.write(seg.fontColor, 0, seg.fontColor.length);
                    buf.write(0x1A);
                    buf.write(seg.fontCode);
                    prevColor = seg.fontColor;
                    prevFont = seg.fontCode;
                }
            }

            if (!t.isEmpty()) {
                byte[] encoded = t.getBytes(ProtocolConstant.GB18030);
                try {
                    buf.write(encoded);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }

        try {
            buf.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return buf.toByteArray();
    }

    private byte[] getColor(int i) {
        if (lineColors != null && i < lineColors.length && lineColors[i] != null)
            return lineColors[i];
        // 默认红调中文
        return new byte[]{FontColor.RED.getCode()};
    }

    private byte getFont(int i) {
        if (lineFonts != null && i < lineFonts.length && lineFonts[i] != 0)
            return lineFonts[i];
        return (byte) JetFileIIFont.CN_16x16.getCode();
    }
}
