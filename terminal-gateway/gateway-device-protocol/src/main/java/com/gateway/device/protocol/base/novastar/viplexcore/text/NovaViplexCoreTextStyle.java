package com.gateway.device.protocol.base.novastar.viplexcore.text;

import lombok.Getter;
import lombok.Setter;

/**
 * ViplexCore 文本样式 —— 对应 RichText XAML 和 metadata textAttributes 的可变参数。
 *
 * <p>与 JetFileII 的 {@code NmgTextStyle} 对齐：协议层运行时样式对象，
 * 由配置层 {@code NovaViplexCoreTextProperties} 通过 yml 绑定创建。</p>
 */
@Setter
@Getter
public class NovaViplexCoreTextStyle {

    /**
     * FlowDocument/Paragraph 字体（默认 宋体）
     */
    private String flowFontFamily = "宋体";

    /**
     * Run 字体（默认 宋体）
     */
    private String runFontFamily = "宋体";

    /**
     * 字号（默认 20），null 表示未设置
     */
    private Integer fontSize = 20;

    /**
     * 文字颜色 ARGB（默认 红色）
     */
    private String textColor = "#FFFF0000";

    /**
     * Run 背景色 ARGB（默认 透明）
     */
    private String runBackground = "#00000000";

    /**
     * FlowDocument 背景色 ARGB（默认 半透明白）
     */
    private String flowBackground = "#00FFFFFF";

    /**
     * xml:lang 语言标记
     */
    private String language = "zh-cn";

    /**
     * 用 override 的非 null 字段覆盖当前值，返回新实例。
     * 调用方传 null 或字段为 null 均保留当前值。
     */
    public NovaViplexCoreTextStyle merge(NovaViplexCoreTextStyle override) {
        if (override == null) return this;
        NovaViplexCoreTextStyle m = new NovaViplexCoreTextStyle();
        m.flowFontFamily = override.flowFontFamily != null ? override.flowFontFamily : this.flowFontFamily;
        m.runFontFamily = override.runFontFamily != null ? override.runFontFamily : this.runFontFamily;
        m.fontSize = override.fontSize != null ? override.fontSize : this.fontSize;
        m.textColor = override.textColor != null ? override.textColor : this.textColor;
        m.runBackground = override.runBackground != null ? override.runBackground : this.runBackground;
        m.flowBackground = override.flowBackground != null ? override.flowBackground : this.flowBackground;
        m.language = override.language != null ? override.language : this.language;
        return m;
    }
}
