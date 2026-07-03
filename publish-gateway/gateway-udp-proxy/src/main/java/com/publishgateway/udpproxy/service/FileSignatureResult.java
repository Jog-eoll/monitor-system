package com.publishgateway.udpproxy.service;

import lombok.Data;

@Data
public class FileSignatureResult {

    private boolean success;
    private byte[] signedEnvelope;
    private String algorithm;
    private String errorMessage;

    public static FileSignatureResult success(byte[] signedEnvelope, String algorithm) {
        FileSignatureResult result = new FileSignatureResult();
        result.setSuccess(true);
        result.setSignedEnvelope(signedEnvelope);
        result.setAlgorithm(algorithm);
        return result;
    }

    public static FileSignatureResult failure(String errorMessage) {
        FileSignatureResult result = new FileSignatureResult();
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        return result;
    }
}
