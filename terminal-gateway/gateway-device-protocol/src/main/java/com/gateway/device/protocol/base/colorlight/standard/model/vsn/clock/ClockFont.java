package com.gateway.device.protocol.base.colorlight.standard.model.vsn.clock;

import com.gateway.device.protocol.base.colorlight.standard.model.vsn.text.LogFont;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模拟时钟字体样式。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClockFont {
    private LogFont time;
    private LogFont fixedText;
    private LogFont date;
    private LogFont week;
    private String fixedTextColor;
    private String weekColor;
    private String dateColor;
}
