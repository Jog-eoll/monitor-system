package com.gateway.udpproxy.controller;

import com.gateway.common.Result;
import com.gateway.udpproxy.entity.UdpProxyRule;
import com.gateway.udpproxy.entity.dto.ConfigDTO;
import com.gateway.udpproxy.manager.UdpProxyRuleManager;
import com.gateway.udpproxy.probe.InfoBoardProbeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/udp-proxy")
public class UdpProxyConfigController {

    @Resource
    private UdpProxyRuleManager proxyRuleManager;

    @Resource
    private InfoBoardProbeService infoBoardProbeService;

    @Value("${registry.client.manufacturer:}")
    private String defaultManufacturer;

    @PostMapping("/config")
    public Result<Map<String, Object>> receiveConfig(@RequestBody @Valid ConfigDTO configDTO) {
        log.info("==========================================");
        log.info("[terminal-gateway] receive config");
        log.info("==========================================");
        log.info("chainId={}, branchCode={}, listenPort={}",
                configDTO.getChainId(), configDTO.getBranchCode(), configDTO.getListenPort());
        log.info("publishGatewayIp={}, infoBoard={}:{}",
                configDTO.getPublishGatewayIp(), configDTO.getInfoBoardIp(), configDTO.getInfoBoardPort());
        log.info("sourceIp={}", resolveSourceIp(configDTO));
        log.info("decryptEnabled={}, manufacturer={}",
                configDTO.getDecryptEnabled(), resolveManufacturer(configDTO.getManufacturer()));

        UdpProxyRule rule = convertToRule(configDTO);
        int actualPort = proxyRuleManager.addOrUpdateRule(rule);

        if (actualPort >= 0) {
            log.info("config applied: ruleId={}, chainId={}, manufacturer={}, actualListenPort={}",
                    rule.getRuleId(), rule.getChainId(), rule.getManufacturer(), actualPort);
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("ruleId", rule.getRuleId());
            result.put("actualListenPort", actualPort);
            return Result.success("config applied", result);
        }
        log.error("config apply failed: chainId={}", configDTO.getChainId());
        return Result.error("config apply failed");
    }

    private UdpProxyRule convertToRule(ConfigDTO dto) {
        UdpProxyRule rule = new UdpProxyRule();
        String ruleId = dto.getChainId() + "_terminal";
        rule.setRuleId(ruleId);
        rule.setRuleName("chain-" + dto.getChainId() + "-terminal-gateway");
        rule.setListenPort(dto.getListenPort());
        rule.setSourceIp(resolveSourceIp(dto));
        rule.setTargetIp(dto.getInfoBoardIp());
        rule.setTargetPort(dto.getInfoBoardPort());
        rule.setDecryptEnabled(dto.getDecryptEnabled() != null ? dto.getDecryptEnabled() : true);
        rule.setStatus("ENABLED");
        rule.setConfigSource("platform");
        rule.setChainId(dto.getChainId());
        rule.setProtocol(dto.getProtocol() != null ? dto.getProtocol() : "UDP");
        rule.setManufacturer(resolveManufacturer(dto.getManufacturer()));
        rule.setAdditionalTcpPorts(dto.getAdditionalTcpPorts());
        rule.setAdditionalUdpPorts(dto.getAdditionalUdpPorts());
        rule.setDynamicPortProxyEnabled(dto.getDynamicPortProxyEnabled());
        rule.setCatchAllProxyPort(dto.getCatchAllProxyPort());
        return rule;
    }

    private String resolveSourceIp(ConfigDTO dto) {
        if (dto != null && StringUtils.hasText(dto.getSourceIp())) {
            return dto.getSourceIp().trim();
        }
        return dto != null ? dto.getPublishGatewayIp() : null;
    }

    private String resolveManufacturer(String inputManufacturer) {
        if (StringUtils.hasText(inputManufacturer)) {
            return inputManufacturer.trim().toLowerCase();
        }
        if (StringUtils.hasText(defaultManufacturer)) {
            return defaultManufacturer.trim().toLowerCase();
        }
        return "unknown";
    }

    @PutMapping("/chain/{chainId}/status")
    public Result<String> setChainStatus(@PathVariable Long chainId,
                                         @RequestParam String status) {
        try {
            String dbStatus = "DISABLE".equalsIgnoreCase(status) ? "DISABLED" : "ENABLED";
            int count = proxyRuleManager.setChainRulesStatus(chainId, dbStatus);
            return Result.success("rule status updated", String.valueOf(count));
        } catch (Exception e) {
            log.error("set chain status failed, chainId={}, status={}", chainId, status, e);
            return Result.error("set chain status failed: " + e.getMessage());
        }
    }

    @PostMapping("/stop/{ruleId}")
    public Result<String> stopRule(@PathVariable String ruleId,
                                   @RequestBody(required = false) Map<String, Object> params) {
        try {
            boolean success = proxyRuleManager.stopRule(ruleId);
            if (success) {
                return Result.success("rule stopped", ruleId);
            }
            return Result.error("rule not found or already stopped");
        } catch (Exception e) {
            log.error("stop rule failed, ruleId={}", ruleId, e);
            return Result.error("stop rule failed: " + e.getMessage());
        }
    }

    @GetMapping("/status/{ruleId}")
    public Result<Map<String, Object>> getRuleStatus(@PathVariable String ruleId) {
        try {
            boolean running = proxyRuleManager.isRuleRunning(ruleId);
            Map<String, Object> status = new java.util.HashMap<>();
            status.put("ruleId", ruleId);
            status.put("running", running);
            status.put("status", running ? "RUNNING" : "STOPPED");
            return Result.success("ok", status);
        } catch (Exception e) {
            log.error("get rule status failed, ruleId={}", ruleId, e);
            return Result.error("get rule status failed: " + e.getMessage());
        }
    }

    @GetMapping("/rules/running")
    public Result<java.util.List<String>> getRunningRules() {
        try {
            return Result.success("ok", proxyRuleManager.getRunningRuleIds());
        } catch (Exception e) {
            log.error("get running rules failed", e);
            return Result.error("get running rules failed: " + e.getMessage());
        }
    }

    @GetMapping("/probe/report")
    public Result<java.util.List<Map<String, Object>>> getProbeReport() {
        try {
            return Result.success("ok", infoBoardProbeService.getLatestProbeResult());
        } catch (Exception e) {
            log.error("get probe report failed", e);
            return Result.error("get probe report failed: " + e.getMessage());
        }
    }

    @GetMapping("/probe/status")
    public Result<Map<String, Object>> getProbeStatus(@RequestParam String ip,
                                                      @RequestParam(required = false) Integer port) {
        try {
            Map<String, Object> status = infoBoardProbeService.getLatestProbeStatus(ip, port);
            return status.isEmpty() ? Result.error("probe status not found: ip=" + ip) : Result.success("ok", status);
        } catch (Exception e) {
            log.error("get probe status failed, ip={}, port={}", ip, port, e);
            return Result.error("get probe status failed: " + e.getMessage());
        }
    }

    @PostMapping("/send-command")
    public Result<String> sendCommand(@RequestBody Map<String, Object> params) {
        String targetIp = (String) params.get("targetIp");
        Object portObj = params.get("targetPort");
        String hexData = (String) params.get("hexData");

        if (!StringUtils.hasText(targetIp)) {
            return Result.error("targetIp is required");
        }
        if (portObj == null) {
            return Result.error("targetPort is required");
        }
        if (!StringUtils.hasText(hexData)) {
            return Result.error("hexData is required");
        }

        int targetPort = Integer.parseInt(String.valueOf(portObj));
        try {
            byte[] data = hexStringToBytes(hexData);
            boolean success = proxyRuleManager.sendCommand(targetIp, targetPort, data);
            if (success) {
                return Result.success("command sent", targetIp + ":" + targetPort);
            }
            return Result.error("command send failed");
        } catch (Exception e) {
            log.error("send command failed, target={}:{}", targetIp, targetPort, e);
            return Result.error("send command failed: " + e.getMessage());
        }
    }

    private byte[] hexStringToBytes(String hex) {
        hex = hex.replaceAll("\\s+", "");
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
