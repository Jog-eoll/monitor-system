package com.gateway.device.protocol.common.serialize;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class BigDecimalWithScaleSerializer extends JsonSerializer<BigDecimal> {

    // 定义需要保留的小数位数
    private static final int SCALE = 9;

    @Override
    public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            // 字段值为 null 时，不输出该字段
            gen.writeNull();
        } else {
            // 1. setScale: 保留 SCALE 位小数
            // 2. RoundingMode.HALF_UP: 使用四舍五入模式
            BigDecimal formatted = value.setScale(SCALE, RoundingMode.HALF_UP);
            // 数值
            // gen.writeNumber(formatted);
            // 字符串
            gen.writeString(formatted.toPlainString());
        }
    }
}
