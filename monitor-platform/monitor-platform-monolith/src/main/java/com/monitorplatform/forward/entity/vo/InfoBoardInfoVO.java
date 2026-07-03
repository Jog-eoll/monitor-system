package com.monitorplatform.forward.entity.vo;

import lombok.Data;

import java.util.LinkedHashMap;

@Data
public class InfoBoardInfoVO extends LinkedHashMap<String, Object> {
    public Boolean getSuccess() {
        Object value = get("success");
        return value == null ? null : Boolean.valueOf(String.valueOf(value));
    }

    public String getMessage() {
        Object value = get("message");
        return value == null ? null : String.valueOf(value);
    }
}
