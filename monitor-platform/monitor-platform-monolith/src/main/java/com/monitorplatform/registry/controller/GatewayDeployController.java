package com.monitorplatform.registry.controller;

import com.monitorplatform.common.entity.Result;
import com.monitorplatform.registry.dto.GatewayDeployLogRequest;
import com.monitorplatform.registry.dto.GatewayRegisterRequest;
import com.monitorplatform.registry.dto.GatewaySelfTestRequest;
import com.monitorplatform.registry.exception.GatewayDeployException;
import com.monitorplatform.registry.service.GatewayDeployService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/deploy")
public class GatewayDeployController {

    @Resource
    private GatewayDeployService gatewayDeployService;

    @PostMapping("/gateway/register")
    public Result<?> registerGateway(@Valid @RequestBody GatewayRegisterRequest request) {
        try {
            return Result.data(gatewayDeployService.register(request));
        } catch (GatewayDeployException e) {
            log.warn("gateway register rejected: code={}, message={}", e.getCode(), e.getMessage());
            return Result.fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("gateway register failed", e);
            return Result.fail(500, "gateway register failed");
        }
    }

    @GetMapping("/gateway/{deviceId}/config")
    public Result<?> getGatewayConfig(@PathVariable String deviceId) {
        try {
            return Result.data(gatewayDeployService.getConfig(deviceId));
        } catch (GatewayDeployException e) {
            return Result.fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("get gateway config failed: deviceId={}", deviceId, e);
            return Result.fail(500, "get gateway config failed");
        }
    }

    @PostMapping("/gateway/{deviceId}/self-test")
    public Result<?> reportGatewaySelfTest(@PathVariable String deviceId,
                                           @RequestBody(required = false) GatewaySelfTestRequest request) {
        try {
            return Result.data(gatewayDeployService.reportSelfTest(deviceId, request));
        } catch (GatewayDeployException e) {
            return Result.fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("report gateway self-test failed: deviceId={}", deviceId, e);
            return Result.fail(500, "report gateway self-test failed");
        }
    }

    @PostMapping("/logs")
    public Result<?> reportDeployLog(@Valid @RequestBody GatewayDeployLogRequest request,
                                     HttpServletRequest servletRequest) {
        try {
            Long id = gatewayDeployService.reportLog(request, clientIp(servletRequest));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", id);
            return Result.data(data);
        } catch (GatewayDeployException e) {
            return Result.fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("report deploy log failed", e);
            return Result.fail(500, "report deploy log failed");
        }
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.trim().isEmpty()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.trim().isEmpty()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
