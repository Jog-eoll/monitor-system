package com.monitorplatform.common.exception;

import lombok.Getter;

/**
 * 业务异常类
 * 
 * @author monitor-platform
 * @date 2026-05-21
 */
@Getter
public class BusinessException extends RuntimeException {
    
    /**
     * 错误码
     */
    private int code;
    
    /**
     * 错误信息
     */
    private String msg;
    
    public BusinessException(int code, String msg) {
        super(msg);
        this.code = code;
        this.msg = msg;
    }
    
    public BusinessException(String msg) {
        this(500, msg);
    }
    
    public BusinessException(int code, String msg, Throwable cause) {
        super(msg, cause);
        this.code = code;
        this.msg = msg;
    }
}
