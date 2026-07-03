package com.gateway.device.protocol.base.novastar.viplexcore.builder;

import com.gateway.device.protocol.base.novastar.viplexcore.text.NovaViplexCoreTextStyle;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;

/**
 * ViplexCore RichText XAML 构建器。
 *
 * <p>生成符合 WPF FlowDocument 规范的 XAML 片段，用于 ARCH_TEXT widget 的 metadata.RichText 字段。</p>
 */
@Slf4j
public class ViplexRichTextBuilder {

    private static final String XAML_NS = "http://schemas.microsoft.com/winfx/2006/xaml/presentation";

    /**
     * 构建 RichText XAML 片段
     */
    public String build(String text, NovaViplexCoreTextStyle props) {
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            // FlowDocument
            Element flow = doc.createElementNS(XAML_NS, "FlowDocument");
            flow.setAttribute("FontFamily", props.getFlowFontFamily());
            flow.setAttribute("FontSize", String.valueOf(props.getFontSize()));
            flow.setAttribute("Foreground", props.getTextColor());
            flow.setAttribute("Background", props.getFlowBackground());
            flow.setAttribute("LineHeight", "1");
            flow.setAttribute("PagePadding", "5,0,5,0");
            flow.setAttribute("AllowDrop", "True");
            doc.appendChild(flow);
            // Paragraph
            Element para = doc.createElementNS(XAML_NS, "Paragraph");
            para.setAttribute("FontFamily", props.getFlowFontFamily());
            para.setAttribute("FontSize", String.valueOf(props.getFontSize()));
            para.setAttribute("Foreground", props.getTextColor());
            flow.appendChild(para);
            // Run
            Element run = doc.createElementNS(XAML_NS, "Run");
            run.setAttribute("FontFamily", props.getRunFontFamily());
            run.setAttribute("Foreground", props.getTextColor());
            run.setAttribute("Background", props.getRunBackground());
            run.setAttribute("xml:lang", props.getLanguage());
            run.setTextContent(text);
            para.appendChild(run);
            // serialize
            StringWriter sw = new StringWriter();
            TransformerFactory.newInstance().newTransformer().transform(
                    new DOMSource(doc), new StreamResult(sw));
            String xml = sw.toString();
            return xml.substring(xml.indexOf("?>") + 2);
        } catch (Exception e) {
            log.warn("构建 RichText 失败", e);
            return "";
        }
    }
}
