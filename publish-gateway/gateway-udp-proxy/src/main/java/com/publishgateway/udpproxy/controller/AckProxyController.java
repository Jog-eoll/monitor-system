package com.publishgateway.udpproxy.controller;

import com.publishgateway.udpproxy.ack.AckProxyLearningService;
import com.publishgateway.udpproxy.ack.AckProxyPendingRequest;
import com.publishgateway.udpproxy.ack.AckProxySample;
import com.publishgateway.udpproxy.ack.AckProxyStatus;
import com.publishgateway.udpproxy.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * ACK proxy learning APIs.
 */
@RestController
@RequestMapping("/secure-publish/ack-proxy")
public class AckProxyController {

    @Resource
    private AckProxyLearningService learningService;

    @GetMapping("/status")
    public Result<AckProxyStatus> status() {
        return Result.success(learningService.getStatus());
    }

    @GetMapping("/samples")
    public Result<List<AckProxySample>> samples(@RequestParam(defaultValue = "50") Integer limit) {
        int safeLimit = limit == null ? 50 : limit;
        return Result.success(learningService.listSamples(safeLimit));
    }

    @GetMapping("/mismatches")
    public Result<List<AckProxySample>> mismatches(@RequestParam(defaultValue = "50") Integer limit) {
        int safeLimit = limit == null ? 50 : limit;
        return Result.success(learningService.listMismatches(safeLimit));
    }

    @GetMapping("/pending")
    public Result<List<AckProxyPendingRequest>> pending(@RequestParam(defaultValue = "50") Integer limit) {
        int safeLimit = limit == null ? 50 : limit;
        return Result.success(learningService.listPending(safeLimit));
    }

    @PostMapping("/clear")
    public Result<AckProxyStatus> clear() {
        learningService.clear();
        return Result.success(learningService.getStatus());
    }
}
