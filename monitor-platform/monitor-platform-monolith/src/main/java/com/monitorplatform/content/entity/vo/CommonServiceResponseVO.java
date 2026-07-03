package com.monitorplatform.content.entity.vo;

import lombok.Data;

@Data
public class CommonServiceResponseVO<T> {
    private Integer code;
    private String msg;
    private T data;
}
