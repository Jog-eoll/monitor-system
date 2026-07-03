package com.monitorplatform.registry.service;

import com.monitorplatform.registry.dto.GatewayDeployLogRequest;
import com.monitorplatform.registry.dto.GatewayRegisterRequest;
import com.monitorplatform.registry.dto.GatewayRegisterResponse;
import com.monitorplatform.registry.dto.GatewaySelfTestRequest;
import com.monitorplatform.registry.dto.GatewaySelfTestResponse;

import java.util.Map;

public interface GatewayDeployService {
    GatewayRegisterResponse register(GatewayRegisterRequest request);

    Map<String, Object> getConfig(String deviceId);

    GatewaySelfTestResponse reportSelfTest(String deviceId, GatewaySelfTestRequest request);

    Long reportLog(GatewayDeployLogRequest request, String clientIp);
}
