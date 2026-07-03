package com.publishgateway.udpproxy.controller;

import com.publishgateway.udpproxy.common.Result;
import com.publishgateway.udpproxy.secure.SecurePublishVerifierService;
import com.publishgateway.udpproxy.secure.jpeg.SecurePublishJpegSignatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/secure-publish")
public class SecurePublishController {

    private final SecurePublishVerifierService verifierService;
    private final SecurePublishJpegSignatureService jpegSignatureService;

    @GetMapping("/status")
    public Result<?> status() {
        java.util.Map<String, Object> status = new java.util.LinkedHashMap<>();
        status.put("package", verifierService.getStatus());
        status.put("jpeg", jpegSignatureService.getStatus());
        return Result.success("success", status);
    }

    @PostMapping("/clear")
    public Result<String> clear() {
        verifierService.clearStatus();
        jpegSignatureService.clearStatus();
        return Result.success("success", null);
    }
}
