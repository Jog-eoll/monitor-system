package com.monitorplatform.registry.exception;

public class GatewayDeployException extends RuntimeException {
    private final int code;

    public GatewayDeployException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
