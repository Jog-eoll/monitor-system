package com.gateway.device.protocol.base.novastar.viplexcore.builder;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gateway.device.protocol.base.novastar.viplexcore.text.NovaViplexCoreTextStyle;
import com.gateway.device.protocol.common.JsonCustomMapper;

import java.util.UUID;

/**
 * 文本页面构建器 — 富文本 ARCH_TEXT 页面。
 *
 * <p>具备状态（默认文本样式），负责构建完整的文本页面 JSON，
 * 包括 RichText XAML 和 metadata。</p>
 */
public class ViplexTextPageBuilder {

    private final NovaViplexCoreTextStyle defaultStyle;

    public ViplexTextPageBuilder(NovaViplexCoreTextStyle defaultStyle) {
        this.defaultStyle = defaultStyle;
    }

    /**
     * 使用默认样式构建文本页面。
     */
    public ObjectNode buildPage(String programId, int pageId,
                                String text, int width, int height) {
        return buildPage(programId, pageId, text, width, height, null);
    }

    /**
     * 构建文本页面（可选样式覆盖）。
     *
     * @param styleOverride 非 null 字段覆盖默认值，null 则全部使用默认
     */
    public ObjectNode buildPage(String programId, int pageId,
                                String text, int width, int height,
                                NovaViplexCoreTextStyle styleOverride) {
        NovaViplexCoreTextStyle effective = defaultStyle.merge(styleOverride);

        // ── widget ──
        ObjectNode widget = JsonCustomMapper.get().createObjectNode();
        widget.put("type", "ARCH_TEXT");
        widget.put("name", "text");
        widget.put("duration", 10000);
        widget.put("repeatCount", 1);
        widget.put("displayRatio", "FULL");
        widget.put("id", ViplexBaseBuilder.WIDGET_BASE_ID);
        widget.put("uuid", UUID.randomUUID().toString());
        widget.put("filesize", 1024);
        widget.put("enable", true);
        widget.put("zOrder", 1);
        widget.put("backgroundColor", "#00FFFFFF");
        widget.put("backgroundDrawable", "");
        widget.put("backgroundMusic", "");
        widget.put("dataSource", "");
        widget.put("originalDataSource", "");
        widget.put("AudioGroup", "");
        widget.set("widgetId", JsonCustomMapper.get().createObjectNode().put("value", 0));
        widget.set("border", ViplexBaseBuilder.buildWidgetBorder());
        widget.set("layout", ViplexBaseBuilder.buildFullWidgetLayout());
        widget.set("inAnimation", ViplexBaseBuilder.buildNoAnimation());
        widget.set("outAnimation", ViplexBaseBuilder.buildNoAnimation());
        widget.set("constraints", JsonCustomMapper.get().createArrayNode()
                .add(ViplexBaseBuilder.buildDefaultConstraint()));
        // 文本 metadata
        widget.set("metadata", new ViplexTextMetadataBuilder().build(text, effective));

        // ── contents ──
        ArrayNode widgetArray = JsonCustomMapper.get().createArrayNode().add(widget);
        ObjectNode contents = ViplexBaseBuilder.buildContents(widgetArray);

        // ── container ──
        ObjectNode container = ViplexBaseBuilder.buildContainer(
                "文本1", "ARCH_TEXT", 0,
                ViplexBaseBuilder.buildContainerBorder(true),
                contents);

        return ViplexBaseBuilder.wrapPage(programId, pageId, container);
    }
}
