package com.gateway.device.protocol.base.novastar.viplexcore.builder;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gateway.device.protocol.base.novastar.viplexcore.text.NovaViplexCoreTextStyle;
import com.gateway.device.protocol.common.JsonCustomMapper;

/**
 * ViplexCore ARCH_TEXT widget metadata JSON 构建器。
 *
 * <p>构建官方对齐的 metadata 结构，包含 content、textAttributes、displayStyle 和 RichText。
 * XAML 生成委托给 {@link ViplexRichTextBuilder}。</p>
 */
public class ViplexTextMetadataBuilder {

    private final ViplexRichTextBuilder richTextBuilder = new ViplexRichTextBuilder();

    /**
     * 构建完整的 metadata JSON
     */
    public ObjectNode build(String text, NovaViplexCoreTextStyle props) {
        // seg → line → paragraph
        ObjectNode seg = JsonCustomMapper.get().createObjectNode();
        seg.put("attributeKey", 0);
        seg.put("content", text);

        ObjectNode line = JsonCustomMapper.get().createObjectNode();
        line.putArray("segs").add(seg);

        ObjectNode paragraph = JsonCustomMapper.get().createObjectNode();
        paragraph.put("horizontalAlignment", "JUSTIFY");
        paragraph.put("verticalAlignment", "TOP");
        paragraph.put("backgroundColor", "#00FFFFFF");
        paragraph.put("lineSpacing", 1.0);
        paragraph.put("letterSpacing", 0);
        paragraph.putArray("lines").add(line);

        // font → attrs → textAttr
        ObjectNode font = JsonCustomMapper.get().createObjectNode();
        font.put("family", JsonCustomMapper.get().createArrayNode().add(props.getRunFontFamily()));
        font.put("size", props.getFontSize());
        font.put("style", "NORMAL");
        font.put("isUnderline", false);

        ObjectNode attrs = JsonCustomMapper.get().createObjectNode();
        attrs.set("font", font);
        attrs.put("textColor", props.getTextColor());
        attrs.put("backgroundColor", props.getRunBackground());
        attrs.put("shadowEnable", false);
        attrs.put("strokeEnable", false);
        attrs.put("letterSpacing", 0);

        ObjectNode textAttr = JsonCustomMapper.get().createObjectNode();
        textAttr.put("key", 0);
        textAttr.set("attributes", attrs);

        // displayStyle: STATIC with scroll
        ObjectNode effects = JsonCustomMapper.get().createObjectNode();
        effects.put("speed", 3);
        effects.put("animation", "MARQUEE_LEFT");
        effects.put("isHeadTail", false);
        effects.put("headTailSpacing", "10");
        effects.put("speedByPixelEnable", false);
        ObjectNode scroll = JsonCustomMapper.get().createObjectNode();
        scroll.set("effects", effects);
        ObjectNode displayStyle = JsonCustomMapper.get().createObjectNode();
        displayStyle.put("type", "STATIC");
        displayStyle.put("singleLine", false);
        displayStyle.set("scrollAttributes", scroll);
        // pageSwitch
        ObjectNode pageSwitch = JsonCustomMapper.get().createObjectNode();
        ObjectNode inAnim = JsonCustomMapper.get().createObjectNode();
        inAnim.put("type", 0);
        inAnim.put("duration", 1000);
        pageSwitch.set("inAnimation", inAnim);
        pageSwitch.put("remainDuration", 10000);
        displayStyle.set("pageSwitchAttributes", pageSwitch);
        // offset
        ObjectNode offset = JsonCustomMapper.get().createObjectNode();
        offset.put("x", 0);
        offset.put("y", 0);
        displayStyle.set("offset", offset);

        // content
        ObjectNode content = JsonCustomMapper.get().createObjectNode();
        content.put("autoPaging", true);
        content.putArray("paragraphs").add(paragraph);
        content.putArray("textAttributes").add(textAttr);
        content.set("displayStyle", displayStyle);
        content.set("backgroundMusic", JsonCustomMapper.get().createObjectNode()
                .put("isTextSync", false).put("duration", 0));

        // metadata top-level
        ObjectNode metadata = JsonCustomMapper.get().createObjectNode();
        metadata.put("LineSpacing", "1px");
        metadata.put("LetterSpacing", "0px");
        metadata.put("VerTextAlignment", "TOP");
        metadata.put("HorTextAlignment", "LEFT");
        metadata.put("ItemSource", "");
        metadata.put("_duritionType", 0);
        metadata.put("textAntialiasing", false);
        metadata.put("RichText", richTextBuilder.build(text, props));
        metadata.set("content", content);

        return metadata;
    }
}
