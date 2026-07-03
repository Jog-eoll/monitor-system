package com.monitorplatform.forward.entity.vo;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class StopGatewayProxyResultVO extends LinkedHashMap<String, Object> {

    public Boolean getSuccess() {
        Object value = get("success");
        return value == null ? null : Boolean.valueOf(String.valueOf(value));
    }

    public String getMessage() {
        Object value = get("message");
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getGatewayResponse() {
        return (Map<String, Object>) get("gatewayResponse");
    }
}
