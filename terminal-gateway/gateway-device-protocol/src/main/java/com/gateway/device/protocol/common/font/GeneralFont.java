package com.gateway.device.protocol.common.font;

import com.gateway.device.protocol.base.novastar.viplexcore.font.NovaStarFontInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;

/**
 * 通用字体枚举 —— 协议无关的字库文件映射。
 *
 * <p>映射已知字库文件（TTF 等），供各厂商 Handler 共用。
 * 调用方可直接用 {@code GeneralFont.SIMHEI} 引用字体，
 * 通过 {@link #getFontFileType()} 区分字体格式。</p>
 *
 * <p>每个常量对应字库目录下的一个字库文件。
 * 默认样式为 "Normal"，多文件多样式场景使用 {@link NovaStarFontInfo} 完整模型。</p>
 */
@Getter
@AllArgsConstructor
public enum GeneralFont {

    ARIAL("Arial", "Arial 标准", "arial.ttf", FontFileType.TTF),
    CALIBRI("Calibri", "Calibri 标准", "calibri.ttf", FontFileType.TTF),
    SIMFANG("仿宋", "仿宋", "simfang.ttf", FontFileType.TTF),
    SIMHEI("黑体", "黑体", "simhei.ttf", FontFileType.TTF),
    SIMKAI("楷体", "楷体", "simkai.ttf", FontFileType.TTF),
    SIMSUN("宋体", "宋体", "simsunb.ttf", FontFileType.TTF),
    STFANGSO("华文仿宋", "华文仿宋", "STFANGSO.TTF", FontFileType.TTF),
    STKAITI("华文楷体", "华文楷体", "STKAITI.TTF", FontFileType.TTF),
    STSONG("华文宋体", "华文宋体", "STSONG.TTF", FontFileType.TTF),
    ;

    /**
     * 字体族名称
     */
    private final String family;

    /**
     * 中文描述
     */
    private final String label;

    /**
     * 字库文件名
     */
    private final String fileName;

    /**
     * 字体文件类型（TTF / FNT / TTC 等）
     */
    private final FontFileType fontFileType;

    /**
     * 按文件名匹配枚举（大小写无关）。
     *
     * @param fileName 字库文件名
     * @return 匹配的枚举常量，未匹配返回 null
     */
    public static GeneralFont ofFile(String fileName) {
        if (fileName == null) return null;
        for (GeneralFont f : values()) {
            if (f.fileName.equalsIgnoreCase(fileName)) return f;
        }
        return null;
    }

    /**
     * 转换为 {@link NovaStarFontInfo}（单文件，默认 Normal 样式，使用枚举内置 fileName）。
     */
    public NovaStarFontInfo toFontInfo() {
        return toFontInfo(fileName);
    }

    /**
     * 转换为 {@link NovaStarFontInfo}，使用指定的文件路径（支持子目录）。
     *
     * @param file 相对于 fontLocalPath 的文件路径（如 "ttf/simhei.ttf"）
     */
    public NovaStarFontInfo toFontInfo(String file) {
        return NovaStarFontInfo.builder()
                .name(family)
                .styles(Collections.singletonList("Normal"))
                .files(Collections.singletonList(file))
                .build();
    }
}
