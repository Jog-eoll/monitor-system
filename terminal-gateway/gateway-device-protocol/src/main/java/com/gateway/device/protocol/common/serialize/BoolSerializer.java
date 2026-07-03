package com.gateway.device.protocol.common.serialize;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

public final class BoolSerializer {
    private BoolSerializer() {
    }

    public static class BoolToIntSerializer extends JsonSerializer<Boolean> {
        @Override
        public void serialize(Boolean value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeNumber(Boolean.TRUE.equals(value) ? 1 : 0);
        }
    }

    public static class IntToBoolDeserializer extends JsonDeserializer<Boolean> {
        @Override
        public Boolean deserialize(JsonParser p, DeserializationContext context) throws IOException {
            int value = p.getValueAsInt(); // 读取 JSON 数字
            return value == 1;
        }
    }
}
