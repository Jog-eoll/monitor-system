package com.publishgateway.udpproxy.controller;

import com.publishgateway.udpproxy.common.Result;
import com.publishgateway.udpproxy.service.ClientFileSignatureRecord;
import com.publishgateway.udpproxy.service.RelayFileSignatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/security/relay/file-signature")
public class RelayFileSignatureController {

    private final RelayFileSignatureService relayFileSignatureService;

    @GetMapping("/status")
    public Result<?> status() {
        return Result.success("success", relayFileSignatureService.getStatus());
    }

    @PostMapping("/clear")
    public Result<String> clear() {
        relayFileSignatureService.clearStatus();
        return Result.success("success", null);
    }

    @PostMapping("/client-record")
    public Result<?> acceptClientRecord(@RequestBody ClientFileSignatureRecord record) {
        return Result.success("success", relayFileSignatureService.acceptClientRecord(record));
    }
}
