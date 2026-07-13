package com.publishgateway.udpproxy.service;

import com.publishgateway.udpproxy.entity.dto.delivery.SecureDeliveryTaskRequest;

/**
 * Downloaded secure-publish file context for post-delivery content reporting.
 */
public class SecurePublishDeliveredFile {

    private final SecureDeliveryTaskRequest.FileRef fileRef;
    private final byte[] data;
    private final String actualHash;

    public SecurePublishDeliveredFile(SecureDeliveryTaskRequest.FileRef fileRef,
                                      byte[] data,
                                      String actualHash) {
        this.fileRef = fileRef;
        this.data = data;
        this.actualHash = actualHash;
    }

    public SecureDeliveryTaskRequest.FileRef getFileRef() {
        return fileRef;
    }

    public byte[] getData() {
        return data;
    }

    public String getActualHash() {
        return actualHash;
    }

    public boolean hasData() {
        return data != null && data.length > 0;
    }
}
