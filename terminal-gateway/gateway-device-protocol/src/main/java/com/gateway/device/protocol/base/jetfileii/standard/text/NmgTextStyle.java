package com.gateway.device.protocol.base.jetfileii.standard.text;

import com.gateway.device.protocol.base.jetfileii.standard.text.constant.Effect;
import com.gateway.device.protocol.base.jetfileii.standard.text.constant.FontColor;
import com.gateway.device.protocol.base.jetfileii.standard.text.constant.JetFileIIFont;
import lombok.Getter;

/**
 * NMG 文字样式参数 —— 字体、颜色、出入花样的不可变封装。
 *
 * <p>字体颜色已解析为字节序列：1B 预定义 / 4B 自定义 BGR ({@code '/' B G R})。
 * 预定义颜色时 {@link #getFontColor()} 返回对应枚举，BGR 时返回 {@code null}。</p>
 */
@Getter
public final class NmgTextStyle {

    private final JetFileIIFont font;
    /**
     * null 表示自定义 BGR 颜色
     * -- GETTER --
     * 预定义颜色时返回枚举，BGR 自定义时返回
     *
     */
    private final FontColor fontColor;
    private final byte[] fontColorBytes;
    private final Effect effectIn;
    private final Effect effectOut;

    public NmgTextStyle(JetFileIIFont font, byte[] fontColorBytes,
                        Effect effectIn, Effect effectOut) {
        this.font = font != null ? font : JetFileIIFont.CN_16x16;
        this.fontColorBytes = fontColorBytes != null ? fontColorBytes.clone() : new byte[]{FontColor.RED.getCode()};
        this.fontColor = this.fontColorBytes.length == 1 ? FontColor.ofCode(this.fontColorBytes[0]) : FontColor.RED;
        this.effectIn = effectIn != null ? effectIn : Effect.RANDOM;
        this.effectOut = effectOut != null ? effectOut : Effect.RANDOM;
    }

    public byte[] getFontColorBytes() {
        return fontColorBytes.clone();
    }

    /**
     * 构建控制前缀字节序列。
     */
    public byte[] buildControlPrefix() {
        NmgControlBuilder cb = NmgControlBuilder.create()
                .font(font)
                .effectIn(effectIn)
                .effectOut(effectOut);
        if (fontColorBytes.length == 1) {
            FontColor fc = FontColor.ofCode(fontColorBytes[0]);
            if (fc != null) {
                cb.fontColor(fc);
            }
        } else {
            cb.fontColorBgr(
                    fontColorBytes[1] & 0xFF,
                    fontColorBytes[2] & 0xFF,
                    fontColorBytes[3] & 0xFF);
        }
        return cb.build();
    }

    /**
     * 构建控制前缀字节序列。
     */
    public byte[] buildControlNoColorPrefix() {
        NmgControlBuilder cb = NmgControlBuilder.create()
                .font(font)
                .effectIn(effectIn)
                .effectOut(effectOut);
        return cb.build();
    }

}
