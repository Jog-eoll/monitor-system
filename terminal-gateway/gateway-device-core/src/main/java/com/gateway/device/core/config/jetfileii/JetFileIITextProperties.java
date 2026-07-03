package com.gateway.device.core.config.jetfileii;

import com.gateway.device.protocol.base.jetfileii.standard.text.constant.Effect;
import com.gateway.device.protocol.base.jetfileii.standard.text.constant.FontColor;
import com.gateway.device.protocol.base.jetfileii.standard.text.constant.JetFileIIFont;
import com.gateway.device.protocol.common.constant.ProtocolConstant;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JetFileII 文本文件配置。
 *
 * <pre>
 * device:
 *   jetfileii:
 *     text:
 *       default-file-name: "default"    # 不含 .nmg 后缀，超 8 字节 GB18030 截断
 *       font-color: RED                 # FontColor 枚举名 或 BGR "255,0,0"
 *       font: CN_16x16                  # Font 枚举名
 *       effect-in: RANDOM               # Effect 枚举名
 *       effect-out: RANDOM              # Effect 枚举名
 * </pre>
 */
@Setter
@Getter
@ConfigurationProperties("device.jetfileii.text")
public class JetFileIITextProperties {

    /**
     * 上传默认文件名 (不含分区和 .nmg 后缀)
     */
    private String defaultFileName = "default";

    /**
     * 【优先】字体颜色：FontColor 枚举名 (如 RED)
     */
    private FontColor fontColor = FontColor.RED;

    /**
     * BGR 格式 "r,g,b" (如 "255,0,0")
     */
    private String fontColorBgr = "255,0,0";

    /**
     * 字体：Font 枚举名 (如 CN_16x16)
     */
    private JetFileIIFont font = JetFileIIFont.CN_16x16;

    /**
     * 入花样：Effect 枚举名 (如 RANDOM)
     */
    private Effect effectIn = Effect.MOVE_LEFT;

    /**
     * 出花样：Effect 枚举名 (如 RANDOM)
     */
    private Effect effectOut = Effect.MOVE_LEFT;

    /**
     * 默认文件名 → GB18030 截断 ≤8 字节 + ".nmg"
     */
    public String resolveFileName() {
        String name = (StringUtils.isNotEmpty(defaultFileName)) ? defaultFileName : "default";
        byte[] bytes = name.getBytes(ProtocolConstant.GB18030);
        if (bytes.length > 8) {
            int cut = 8;
            while (cut > 0 && (bytes[cut] & 0xC0) == 0x80) cut--;
            name = new String(bytes, 0, cut, ProtocolConstant.GB18030);
        }
        return name + ".nmg";
    }
}
