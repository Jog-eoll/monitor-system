package com.publishgateway.udpproxy.controller;

import com.publishgateway.udpproxy.common.Result;
import com.publishgateway.udpproxy.service.ContentReleaseTokenIssueRequest;
import com.publishgateway.udpproxy.service.ContentReleaseTokenIssueResponse;
import com.publishgateway.udpproxy.service.ContentReleaseTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/security/content-token")
public class ContentReleaseTokenController {

    private final ContentReleaseTokenService contentReleaseTokenService;

    @PostMapping("/issue")
    public Result<ContentReleaseTokenIssueResponse> issue(@RequestBody ContentReleaseTokenIssueRequest request) {
        ContentReleaseTokenIssueResponse response = contentReleaseTokenService.issue(request);
        if (response == null || !response.isIssued()) {
            String error = response == null ? "TOKEN_ISSUE_FAILED" : response.getError();
            return Result.error(400, error, response);
        }
        return Result.success("success", response);
    }

    @GetMapping("/status")
    public Result<?> status() {
        return Result.success("success", contentReleaseTokenService.getStatus());
    }

    @PostMapping("/clear")
    public Result<String> clear() {
        contentReleaseTokenService.clearStatus();
        return Result.success("success", null);
    }
}
