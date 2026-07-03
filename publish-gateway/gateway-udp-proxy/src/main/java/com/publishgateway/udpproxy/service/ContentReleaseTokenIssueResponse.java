package com.publishgateway.udpproxy.service;

import lombok.Data;

@Data
public class ContentReleaseTokenIssueResponse {

    private boolean issued;
    private String tokenId;
    private String fileId;
    private ContentReleaseTokenMetadata metadata;
    private String signedEnvelopeBase64;
    private String error;
}
