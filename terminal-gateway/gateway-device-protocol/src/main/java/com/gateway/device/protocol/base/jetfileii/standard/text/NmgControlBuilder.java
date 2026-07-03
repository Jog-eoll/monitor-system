package com.gateway.device.protocol.base.jetfileii.standard.text;

import com.gateway.device.protocol.base.jetfileii.standard.text.constant.*;
import com.gateway.device.protocol.common.constant.ProtocolConstant;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * TEXT FILE 控制前缀构建器 — 配置项独立存储，build() 时按协议固定顺序组装。
 *
 * <p>所有枚举型配置项可直接传入 {@link NmgResources} 中的枚举值。
 * 背景色支持预定义颜色 ({@link BgColor}) 和自定义 BGR 颜色。</p>
 *
 * <p>输出为可变长控制前缀（默认 38B，使用自定义 BGR 背景色时 +3B）。</p>
 */
public final class NmgControlBuilder {

    // ── 配置字段 (独立存储, 默认值与官方工具一致) ──────
    private String version = "11003";
    private ProcessMode procMode = ProcessMode.AUTO;
    private int lineSpace = 1;
    private int staySec = 2;
    private StayTimeUnit stayUnit = StayTimeUnit.SECOND_4D;
    private AlignV vAlign = AlignV.CENTER;
    private AlignH hAlign = AlignH.LEFT;
    private Effect effectIn = Effect.RANDOM;
    private Effect effectOut = Effect.RANDOM;
    private Speed speed = Speed.S2;
    private FontColor fontColor = FontColor.RED;
    /**
     * null=预定义, non-null=自定义 BGR 字体颜色 (含 '/' 前缀)
     */
    private byte[] fontColorData = null;
    /**
     * null=预定义, non-null=自定义 BGR (含 '/' 前缀)
     */
    private byte[] bgColorData = null;
    private BgColor bgColorPre = BgColor.BLACK;
    private JetFileIIFont font = JetFileIIFont.CN_16x16;
    private Blink blink = Blink.OFF;

    private NmgControlBuilder() {
    }

    /**
     * 构建默认控制前缀 (38B)。
     */
    public static byte[] buildDefault() {
        return new NmgControlBuilder().build();
    }

    /**
     * 创建自定义构建器。
     */
    public static NmgControlBuilder create() {
        return new NmgControlBuilder();
    }

    // ── 配置方法 ────────────────────────────────────────

    public NmgControlBuilder version(String ver) {
        this.version = ver;
        return this;
    }

    public NmgControlBuilder processMode(ProcessMode m) {
        this.procMode = m;
        return this;
    }

    public NmgControlBuilder lineSpacing(int px) {
        this.lineSpace = Math.min(9, Math.max(0, px));
        return this;
    }

    public NmgControlBuilder stayTimeSec(int sec) {
        this.staySec = Math.min(9999, Math.max(0, sec));
        return this;
    }

    public NmgControlBuilder stayTimeUnit(StayTimeUnit u) {
        this.stayUnit = u;
        return this;
    }

    public NmgControlBuilder vAlign(AlignV a) {
        this.vAlign = a;
        return this;
    }

    public NmgControlBuilder hAlign(AlignH a) {
        this.hAlign = a;
        return this;
    }

    public NmgControlBuilder effectIn(Effect e) {
        this.effectIn = e;
        return this;
    }

    public NmgControlBuilder effectOut(Effect e) {
        this.effectOut = e;
        return this;
    }

    public NmgControlBuilder speed(Speed s) {
        this.speed = s;
        return this;
    }

    public NmgControlBuilder fontColor(FontColor c) {
        this.fontColor = c;
        this.fontColorData = null;
        return this;
    }

    /**
     * 自定义 BGR 字体颜色 (0x1C '/' B G R)
     *
     * @param r 红 0–255
     * @param g 绿 0–255
     * @param b 蓝 0–255
     */
    public NmgControlBuilder fontColorBgr(int r, int g, int b) {
        this.fontColorData = NmgResources.customBgr(r, g, b);
        this.fontColor = null;
        return this;
    }

    public NmgControlBuilder font(JetFileIIFont f) {
        this.font = f;
        return this;
    }

    public NmgControlBuilder blink(Blink b) {
        this.blink = b;
        return this;
    }

    // ── 背景色 (预定义) ──────────────────────────────

    /**
     * 预定义背景色
     */
    public NmgControlBuilder bgColor(BgColor c) {
        this.bgColorPre = c;
        this.bgColorData = null;
        return this;
    }

    // ── 背景色 (自定义 BGR) ──────────────────────────

    /**
     * 自定义 BGR 背景色。
     *
     * @param r 红 0–255
     * @param g 绿 0–255
     * @param b 蓝 0–255
     */
    public NmgControlBuilder bgColorBgr(int r, int g, int b) {
        this.bgColorData = NmgResources.customBgr(r, g, b);
        this.bgColorPre = null;
        return this;
    }

    /**
     * 纯白色背景 (R=255 G=255 B=255)
     */
    public NmgControlBuilder bgColorWhite() {
        return bgColorBgr(255, 255, 255);
    }

    /**
     * 纯蓝色背景 (R=0 G=0 B=255)
     */
    public NmgControlBuilder bgColorBlue() {
        return bgColorBgr(0, 0, 255);
    }

    // ── 便捷预设 ──────────────────────────────────────

    /**
     * 快捷预设：中文显示 (16×16, 红色)
     */
    public NmgControlBuilder presetChinese() {
        this.font = JetFileIIFont.CN_16x16;
        this.fontColor = FontColor.RED;
        return this;
    }

    /**
     * 快捷预设：英文显示 (16×9, 绿白渐变)
     */
    public NmgControlBuilder presetEnglish() {
        this.font = JetFileIIFont.EN_16x9;
        this.fontColor = FontColor.GREEN_WHITE_H;
        return this;
    }

    // ── 查询方法 ──────────────────────────────────────

    /**
     * 获取 0x1C 之后的字体颜色字节序列 (用于行内切换)
     */
    public byte[] getFontColorBytes() {
        if (fontColorData != null) return fontColorData.clone();
        byte code = fontColor != null ? fontColor.getCode() : FontColor.RED.getCode();
        return new byte[]{code};
    }

    // ── 构建 (按协议文档 §15 固定顺序写入) ────────────
    // 0x1B(处理模式) 依文档要求在所有其他控制字符之前, 仅 0x18(协议扩展) 更优先

    /**
     * 按协议固定顺序组装控制前缀
     */
    public byte[] build() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        // 1. 0x18 协议扩展：版本号
        byte[] ver = version.getBytes(ProtocolConstant.GB18030);
        buf.write(0x18);
        buf.write(ver.length);
        buf.write(ver, 0, ver.length);

        // 2. 0x1B 处理模式
        buf.write(0x1B);
        buf.write('0');
        buf.write(procMode.getCode());

        // 3. 0x08 行间距
        buf.write(0x08);
        buf.write('0' + lineSpace);

        // 4. 0x0E 停留时间 (§15.4)
        buf.write(0x0E);
        buf.write(stayUnit.getCode());
        String timeStr = String.format("%0" + stayUnit.getDigits() + "d", staySec);
        for (char c : timeStr.toCharArray()) buf.write(c);

        // 5. 0x1F 垂直对齐
        buf.write(0x1F);
        buf.write(vAlign.getCode());

        // 6. 0x1E 水平对齐
        buf.write(0x1E);
        buf.write(hAlign.getCode());

        // 7. 0x0A 入花样: 0x0A 'I' <code>
        buf.write(0x0A);
        buf.write('I');
        buf.write(effectIn.getCode());

        // 8. 0x0A 出花样: 0x0A 'O' <code>
        buf.write(0x0A);
        buf.write('O');
        buf.write(effectOut.getCode());

        // 9. 0x0F 速度
        buf.write(0x0F);
        buf.write(speed.getCode());

        // 10. 0x1C 字体颜色 (预定义 2B / 自定义BGR 5B)
        buf.write(0x1C);
        if (fontColorData != null) {
            buf.write(fontColorData, 0, fontColorData.length);
        } else {
            buf.write(fontColor != null ? fontColor.getCode() : FontColor.RED.getCode());
        }

        // 11. 0x1D 背景色 (预定义 2B / 自定义BGR 5B)
        buf.write(0x1D);
        if (bgColorData != null) {
            buf.write(bgColorData, 0, bgColorData.length);
        } else {
            buf.write(bgColorPre != null ? bgColorPre.getCode() : '0');
        }

        // 12. 0x1A 字体及大小
        buf.write(0x1A);
        buf.write(font.getCode());

        // 13. 0x07 闪烁
        buf.write(0x07);
        buf.write(blink.getCode());

        try {
            buf.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return buf.toByteArray();
    }
}
