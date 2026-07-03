package com.gateway.device.core.config.novastar;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ViplexCore 文本样式 yml 配置。
 *
 * <pre>
 * device:
 *   novastar:
 *     viplexcore:
 *       text:
 *         flow-font-family: "Microsoft YaHei"
 *         run-font-family: "宋体"
 *         font-size: 20
 *         text-color: "#FFFF0000"
 *         run-background: "#00000000"
 *         flow-background: "#00FFFFFF"
 *         language: "zh-cn"
 * </pre>
 */
@Setter
@Getter
@ConfigurationProperties(prefix = "device.novastar.viplexcore.text")
public class NovaViplexCoreTextProperties {

    /**
     * FlowDocument/Paragraph 字体
     */
    private String flowFontFamily = "宋体";

    /**
     * Run 字体
     */
    private String runFontFamily = "宋体";

    /**
     * 字号
     */
    private Integer fontSize = 20;

    /**
     * 文字颜色 ARGB
     */
    private String textColor = "#FFFF0000";

    /**
     * Run 背景色 ARGB
     */
    private String runBackground = "#00000000";

    /**
     * FlowDocument 背景色 ARGB
     */
    private String flowBackground = "#00FFFFFF";

    /**
     * xml:lang 语言标记
     */
    private String language = "zh-cn";
}
