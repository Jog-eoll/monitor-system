package com.publishgateway.udpproxy.service;

import lombok.Data;

@Data
public class ContentReleaseTokenVerifyResult {

    private boolean allowed;
    private boolean protectedContent;
    private String reason;
    private String tokenId;
    private String fileId;

    public static ContentReleaseTokenVerifyResult allow(String reason) {
        ContentReleaseTokenVerifyResult result = new ContentReleaseTokenVerifyResult();
        result.setAllowed(true);
        result.setReason(reason);
        return result;
    }

    public static ContentReleaseTokenVerifyResult deny(String reason, boolean protectedContent) {
        ContentReleaseTokenVerifyResult result = new ContentReleaseTokenVerifyResult();
        result.setAllowed(false);
        result.setProtectedContent(protectedContent);
        result.setReason(reason);
        return result;
    }
}
