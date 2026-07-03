package com.publishgateway.udpproxy.service;

import lombok.Data;

@Data
public class FileSignatureVerifyResult {

    private boolean verified;
    private String errorMessage;

    public static FileSignatureVerifyResult success() {
        FileSignatureVerifyResult result = new FileSignatureVerifyResult();
        result.setVerified(true);
        return result;
    }

    public static FileSignatureVerifyResult failure(String errorMessage) {
        FileSignatureVerifyResult result = new FileSignatureVerifyResult();
        result.setVerified(false);
        result.setErrorMessage(errorMessage);
        return result;
    }
}
