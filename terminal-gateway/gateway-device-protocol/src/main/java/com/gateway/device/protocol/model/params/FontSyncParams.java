package com.gateway.device.protocol.model.params;

import com.gateway.device.protocol.base.jetfileii.standard.text.constant.JetFileIIFont;
import com.gateway.device.protocol.common.font.GeneralFont;
import com.gateway.device.protocol.model.params.depend.CommandParams;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 字体同步参数 — FONTS_SYNC 公共能力，协议无关。
 *
 * <p>各厂商 Handler 按需读取对应字段，互不干扰：</p>
 * <ul>
 *   <li>JetFileII   → {@code jetFileIIFonts}（.fnt 点阵字库，枚举列表）</li>
 *   <li>NovaStar / ColorLight → {@code fonts}（通用字体枚举列表，按 {@code fontFileType} 过滤）</li>
 *   <li>均为空      → 递归扫描自动发现</li>
 * </ul>
 */
@Data
@Builder
public class FontSyncParams implements CommandParams {

    /**
     * JetFileII .fnt 点阵字体枚举列表（从本地字库目录加载 .fnt 文件）
     */
    private List<JetFileIIFont> jetFileIIFonts;

    /**
     * 通用字体枚举列表（TTF 等），NovaStar / ColorLight 按 {@code fontFileType} 过滤使用
     */
    private List<GeneralFont> fonts;
}
