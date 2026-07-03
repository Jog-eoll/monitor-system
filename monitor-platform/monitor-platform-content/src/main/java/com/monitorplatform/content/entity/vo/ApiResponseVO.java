package com.monitorplatform.content.entity.vo;

import lombok.Data;

@Data
public class ApiResponseVO<T> {
    private Integer code;
    private String msg;
    private T data;

    public static <T> ApiResponseVO<T> success(T data) {
        ApiResponseVO<T> resp = new ApiResponseVO<>();
        resp.setCode(200);
        resp.setMsg("success");
        resp.setData(data);
        return resp;
    }

    public static <T> ApiResponseVO<T> error(String msg) {
        ApiResponseVO<T> resp = new ApiResponseVO<>();
        resp.setCode(500);
        resp.setMsg(msg);
        resp.setData(null);
        return resp;
    }
}
