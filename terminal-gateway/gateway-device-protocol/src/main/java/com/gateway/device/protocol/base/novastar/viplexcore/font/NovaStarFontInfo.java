package com.gateway.device.protocol.base.novastar.viplexcore.font;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * NovaStar ViplexCore TrueType 字体信息。
 *
 * <p>描述一个待同步到终端的 TTF 字体：字体名、支持的样式列表、以及每个样式对应的
 * TTF 文件名。样式与文件按索引一一对应。</p>
 *
 * <p>对应 SDK {@code nvUpdateFontAsync} 的 {@code fonts[].name / style[] / file[]} 字段。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NovaStarFontInfo {

    /**
     * 字体名，如 "Arial"、"宋体"
     */
    private String name;

    /**
     * 样式列表，如 ["Bold", "Italic", "Normal"]
     */
    private List<String> styles;

    /**
     * 对应 TTF 文件名列表，与 styles 一一对应
     */
    private List<String> files;
}
