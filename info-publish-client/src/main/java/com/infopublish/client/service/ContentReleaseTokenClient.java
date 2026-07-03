package com.infopublish.client.service;

import com.infopublish.client.entity.dto.ContentAuditItem;
import com.infopublish.client.entity.dto.ContentReleaseTokenIssueResponse;

public interface ContentReleaseTokenClient {

    ContentReleaseTokenIssueResponse issue(ContentAuditItem item, String scanResult, String riskLevel);

    boolean isEnabled();
}
