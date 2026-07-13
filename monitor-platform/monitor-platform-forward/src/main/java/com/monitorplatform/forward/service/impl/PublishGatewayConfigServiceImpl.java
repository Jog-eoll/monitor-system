package com.monitorplatform.forward.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitorplatform.forward.config.MqttDispatchProperties;
import com.monitorplatform.forward.entity.DeviceMqttCommand;
import com.monitorplatform.forward.entity.TaskChainConfig;
import com.monitorplatform.forward.entity.TaskChainNode;
import com.monitorplatform.forward.feign.DeviceFeignClient;
import com.monitorplatform.forward.mapper.TaskChainConfigMapper;
import com.monitorplatform.forward.mapper.TaskChainNodeMapper;
import com.monitorplatform.forward.service.MqttCommandPublishService;
import com.monitorplatform.forward.service.PublishGatewayConfigService;
import com.monitorplatform.mqtt.core.dto.MqttCommandMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 发布网关配置服务实现类
 * 只保留核心的自动下发功能
 */
@Slf4j
@Service
public class PublishGatewayConfigServiceImpl implements PublishGatewayConfigService {

    @Resource
    private TaskChainConfigMapper chainConfigMapper;

    @Resource
    private TaskChainNodeMapper chainNodeMapper;

    @Autowired(required = false)
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Resource
    private DeviceFeignClient deviceFeignClient;

    /**
     * 发布网关默认地址
     */
    @Value("${forward.gateway.default-url:http://192.168.1.25:8092}")
    private String defaultGatewayUrl;

    // 设备类型常量
    private static final String TYPE_PUBLISH_GATEWAY = "publish_gateway";
    private static final String TYPE_TERMINAL_GATEWAY = "terminal_encrypt_gateway";
    private static final String TYPE_INFO_BOARD = "info_board";
    private static final String TYPE_PUBLISH_SERVER = "publish_server";

    /**
     * 信息发布客户端默认端口
     */
    @Value("${forward.client.default-port:7081}")
    private int defaultClientPort;

    @Resource
    private MqttCommandPublishService mqttCommandPublishService;

    @Resource
    private MqttDispatchProperties mqttDispatchProperties;

    @Override
    public Map<String, Object> deployChainToPublishGateway(Long chainId) {
        return deployChainToPublishGateway(chainId, "auto");
    }

    @Override
    public Map<String, Object> deployChainToPublishGateway(Long chainId, String triggerSource) {
        // 根据触发来源确定日志标识
        String logPrefix = "manual".equals(triggerSource) ? "【手动下发】" : "【自动下发】";
        
        log.info("==========================================");
        log.info("  {} 开始构建并下发链路配置", logPrefix);
        log.info("  链路ID: {}", chainId);
        log.info("==========================================");

        // 1. 查询链路配置
        TaskChainConfig config = chainConfigMapper.selectById(chainId);
        if (config == null || config.getDeleted() == 1) {
            throw new IllegalArgumentException("链路不存在或已删除: " + chainId);
        }

        // 2. 查询所有节点
        LambdaQueryWrapper<TaskChainNode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskChainNode::getChainId, chainId);
        List<TaskChainNode> allNodes = chainNodeMapper.selectList(wrapper);

        if (CollUtil.isEmpty(allNodes)) {
            throw new IllegalArgumentException("链路没有任何节点");
        }

        // 3. 查找发布加密网关节点
        TaskChainNode publishGatewayNode = allNodes.stream()
                .filter(n -> TYPE_PUBLISH_GATEWAY.equals(n.getDeviceType()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("链路缺少发布加密网关节点"));

        // 4. 查找发布服务器节点（Sigma主机），用于提取 sourceIp 白名单
        TaskChainNode publishServerNode = allNodes.stream()
                .filter(n -> "publish_server".equals(n.getDeviceType()))
                .findFirst()
                .orElse(null); // 可选节点，不强制要求

        // 4. 查找所有终端加密网关（分支起点）
        List<TaskChainNode> terminalGateways = allNodes.stream()
                .filter(n -> TYPE_TERMINAL_GATEWAY.equals(n.getDeviceType()))
                .collect(Collectors.toList());

        if (CollUtil.isEmpty(terminalGateways)) {
            throw new IllegalArgumentException("链路缺少终端加密网关节点");
        }

        // 5. 为每个分支构建配置并下发（同时下发到发布网关和终端网关）
        List<Map<String, Object>> deployResults = new ArrayList<>();
        int publishSuccessCount = 0;
        int publishFailedCount = 0;
        int terminalSuccessCount = 0;
        int terminalFailedCount = 0;
        int clientSuccessCount = 0;
        int clientFailedCount = 0;

        // 查询发布网关MAC（用于客户端ARP绑定）
        String publishGatewayMac = queryDeviceMac(publishGatewayNode.getDeviceId());

        for (int i = 0; i < terminalGateways.size(); i++) {
            TaskChainNode terminalGw = terminalGateways.get(i);
            String branchCode = "B" + (i + 1);
            
            try {
                // 查找该终端网关下的情报板
                TaskChainNode infoBoard = findChildOfType(terminalGw.getId(), TYPE_INFO_BOARD, allNodes);
                if (infoBoard == null) {
                    log.warn("{} 终端加密网关[{}]下未找到情报板，跳过", logPrefix, terminalGw.getDeviceId());
                    publishFailedCount++;
                    terminalFailedCount++;
                    continue;
                }

                // 查询情报板厂家（只查一次，分别传给终端网关和发布网关）
                String manufacturer = queryManufacturer(infoBoard.getDeviceId());

                // 先下发到终端网关（获取实际分配的监听端口）
                int actualTerminalPort = doDeployToTerminalGateway(
                        chainId, branchCode,
                        publishGatewayNode, terminalGw, infoBoard, publishServerNode, manufacturer
                );

                boolean terminalSuccess = (actualTerminalPort > 0);

                // 再下发到发布网关（使用终端网关实际分配的端口）
                boolean publishSuccess = false;
                if (terminalSuccess) {
                    publishSuccess = doDeployToPublishGateway(
                            chainId, branchCode,
                            publishGatewayNode, terminalGw, infoBoard, publishServerNode,
                            actualTerminalPort, manufacturer, publishGatewayMac
                    );
                } else {
                    log.error("{} 终端网关部署失败，跳过发布网关部署: chainId={}, branchCode={}",
                            logPrefix, chainId, branchCode);
                }

                Map<String, Object> result = new HashMap<>();
                result.put("branchCode", branchCode);
                result.put("publishGatewaySuccess", publishSuccess);
                result.put("terminalGatewaySuccess", terminalSuccess);
                result.put("terminalGatewayIp", terminalGw.getDeviceIp());
                deployResults.add(result);

                if (publishSuccess) {
                    publishSuccessCount++;
                } else {
                    publishFailedCount++;
                }

                if (terminalSuccess) {
                    terminalSuccessCount++;
                } else {
                    terminalFailedCount++;
                }

                // 双端下发均成功时，将本分支所有节点状态更新为"在线"
                if (publishSuccess && terminalSuccess) {
                    updateBranchNodeStatus(terminalGw, infoBoard, publishGatewayNode, allNodes, "在线");
                }

                // 客户端配置由发布网关中转，不再直接下发
                // 发布网关收到配置后，通过 clientIp 字段自动转发给 PC 客户端
                boolean clientSuccess = publishSuccess && terminalSuccess;
                result.put("publishClientSuccess", clientSuccess);
                if (clientSuccess) {
                    clientSuccessCount++;
                }

            } catch (Exception e) {
                log.error("{} 下发分支配置失败: terminalGw={}", logPrefix, terminalGw.getDeviceId(), e);
                publishFailedCount++;
                terminalFailedCount++;
            }
        }

        log.info("==========================================");
        log.info("  {} 链路配置下发完成", logPrefix);
        log.info("  分支总数: {}", deployResults.size());
        log.info("  发布网关 - 成功: {}, 失败: {}", publishSuccessCount, publishFailedCount);
        log.info("  终端网关 - 成功: {}, 失败: {}", terminalSuccessCount, terminalFailedCount);
        log.info("  发布客户端 - 成功: {}, 失败: {}", clientSuccessCount, clientFailedCount);
        log.info("==========================================");

        Map<String, Object> summary = new HashMap<>();
        summary.put("chainId", chainId);
        summary.put("totalBranches", deployResults.size());
        
        Map<String, Object> publishGatewayStats = new HashMap<>();
        publishGatewayStats.put("successCount", publishSuccessCount);
        publishGatewayStats.put("failedCount", publishFailedCount);
        summary.put("publishGateway", publishGatewayStats);
        
        Map<String, Object> terminalGatewayStats = new HashMap<>();
        terminalGatewayStats.put("successCount", terminalSuccessCount);
        terminalGatewayStats.put("failedCount", terminalFailedCount);
        summary.put("terminalGateway", terminalGatewayStats);

        Map<String, Object> publishClientStats = new HashMap<>();
        publishClientStats.put("successCount", clientSuccessCount);
        publishClientStats.put("failedCount", clientFailedCount);
        summary.put("publishClient", publishClientStats);
        
        summary.put("details", deployResults);

        // 下发完成后同步链路整体状态（根据节点状态重新计算）
        syncChainStatusAfterDeploy(chainId);

        return summary;
    }

    /**
     * 将指定分支涉及的节点状态更新为目标状态
     */
    private void updateBranchNodeStatus(TaskChainNode terminalGw, TaskChainNode infoBoard,
                                        TaskChainNode publishGwNode, List<TaskChainNode> allNodes,
                                        String status) {
        // 本分支涉及的节点：发布网关、终端网关、情报板
        List<Long> nodeIds = new ArrayList<>();
        if (publishGwNode != null) nodeIds.add(publishGwNode.getId());
        if (terminalGw != null) nodeIds.add(terminalGw.getId());
        if (infoBoard != null) nodeIds.add(infoBoard.getId());
        // 同时更新发布服务器节点
        allNodes.stream()
                .filter(n -> "publish_server".equals(n.getDeviceType()))
                .map(TaskChainNode::getId)
                .forEach(nodeIds::add);

        for (Long nodeId : nodeIds) {
            TaskChainNode node = new TaskChainNode();
            node.setId(nodeId);
            node.setNodeStatus(status);
            node.setUpdateTime(LocalDateTime.now());
            chainNodeMapper.updateById(node);
        }
        log.info("【下发后更新节点状态】分支节点状态已更新为: {}, 节点数: {}", status, nodeIds.size());
    }

    /**
     * 下发完成后同步链路状态
     * 根据节点在线数量重新计算链路 status
     */
    private void syncChainStatusAfterDeploy(Long chainId) {
        try {
            TaskChainConfig config = chainConfigMapper.selectById(chainId);
            if (config == null) return;

            LambdaQueryWrapper<TaskChainNode> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(TaskChainNode::getChainId, chainId);
            List<TaskChainNode> nodes = chainNodeMapper.selectList(wrapper);

            if (nodes.isEmpty()) {
                config.setStatus(0);
            } else {
                long onlineCount = nodes.stream().filter(n -> "在线".equals(n.getNodeStatus())).count();
                long errorCount = nodes.stream().filter(n -> "异常".equals(n.getNodeStatus())).count();
                long offlineCount = nodes.size() - onlineCount - errorCount;

                if (errorCount > 0) {
                    config.setStatus(2);
                } else if (onlineCount == nodes.size()) {
                    config.setStatus(1);
                    config.setEnabled(1); // 下发成功后启用链路
                } else if (onlineCount > 0) {
                    config.setStatus(1); // 部分在线也视为可用
                    config.setEnabled(1); // 下发成功后启用链路
                } else {
                    config.setStatus(0);
                }
            }
            config.setUpdateTime(LocalDateTime.now());
            chainConfigMapper.updateById(config);
            log.info("【下发后同步链路状态】chainId={}, status={}, enabled={}", chainId, config.getStatus(), config.getEnabled());
        } catch (Exception e) {
            log.error("【下发后同步链路状态】失败: chainId={}", chainId, e);
        }
    }

    // ===== MQTT 下发模式常量 =====

    private static final String DISPATCH_MODE_HTTP = "http";
    private static final String DISPATCH_MODE_MQTT = "mqtt";
    private static final String DISPATCH_MODE_DUAL = "dual";

    /**
     * 判断是否使用 MQTT 下发（mqtt 或 dual 模式）
     */
    private boolean shouldUseMqtt() {
        String mode = mqttDispatchProperties.getMode();
        return DISPATCH_MODE_MQTT.equalsIgnoreCase(mode)
                || DISPATCH_MODE_DUAL.equalsIgnoreCase(mode);
    }

    /**
     * 判断是否同时走 HTTP（http 或 dual 模式）
     */
    private boolean shouldUseHttp() {
        String mode = mqttDispatchProperties.getMode();
        return DISPATCH_MODE_HTTP.equalsIgnoreCase(mode)
                || DISPATCH_MODE_DUAL.equalsIgnoreCase(mode);
    }

    private String resolveMqttGatewayDeviceId(TaskChainNode gatewayNode) {
        if (gatewayNode == null) {
            return null;
        }
        String logicalDeviceId = normalizeText(gatewayNode.getDeviceId());
        if (isRoutableMqttGatewayId(logicalDeviceId)) {
            return logicalDeviceId;
        }

        String explicitMqttId = queryConfiguredMqttDeviceId(gatewayNode);
        if (explicitMqttId != null) {
            log.info("[MQTT下发] 链路节点 ID 映射为 MQTT agent ID: logicalDeviceId={}, mqttDeviceId={}",
                    logicalDeviceId, explicitMqttId);
            return explicitMqttId;
        }

        String derivedMqttId = deriveMqttGatewayDeviceId(gatewayNode.getDeviceType(), gatewayNode.getDeviceIp());
        if (derivedMqttId != null) {
            log.warn("[MQTT下发] 未查询到显式 MQTT agent ID，按网关 IP 推导: logicalDeviceId={}, deviceType={}, deviceIp={}, mqttDeviceId={}",
                    logicalDeviceId, gatewayNode.getDeviceType(), gatewayNode.getDeviceIp(), derivedMqttId);
            return derivedMqttId;
        }

        return logicalDeviceId;
    }

    @SuppressWarnings("unchecked")
    private String queryConfiguredMqttDeviceId(TaskChainNode gatewayNode) {
        String logicalDeviceId = normalizeText(gatewayNode.getDeviceId());
        String deviceType = normalizeText(gatewayNode.getDeviceType());
        if (logicalDeviceId == null || deviceType == null || deviceFeignClient == null) {
            return null;
        }
        try {
            Map<String, Object> resp = deviceFeignClient.getDeviceDetail(deviceType, logicalDeviceId);
            if (resp == null || !Integer.valueOf(200).equals(resp.get("code"))) {
                return null;
            }
            Object dataObj = resp.get("data");
            if (!(dataObj instanceof Map)) {
                return null;
            }
            Map<String, Object> data = (Map<String, Object>) dataObj;
            String candidate = firstText(data.get("mqttDeviceId"), data.get("mqttClientId"), data.get("instanceId"));
            if (isRoutableMqttGatewayId(candidate)) {
                return candidate;
            }
            Object specificObj = data.get("specificAttributes");
            if (specificObj instanceof Map) {
                Map<String, Object> specific = (Map<String, Object>) specificObj;
                candidate = firstText(specific.get("mqttDeviceId"), specific.get("mqttClientId"), specific.get("instanceId"));
                if (isRoutableMqttGatewayId(candidate)) {
                    return candidate;
                }
            }
        } catch (Exception e) {
            log.warn("[MQTT下发] 查询 MQTT agent ID 映射失败: logicalDeviceId={}, error={}",
                    logicalDeviceId, e.getMessage());
        }
        return null;
    }

    private String deriveMqttGatewayDeviceId(String deviceType, String deviceIp) {
        String ip = normalizeText(deviceIp);
        if (ip == null) {
            return null;
        }
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return null;
        }
        String lastOctet = parts[3];
        try {
            int value = Integer.parseInt(lastOctet);
            if (value < 0 || value > 255) {
                return null;
            }
        } catch (NumberFormatException e) {
            return null;
        }

        if (TYPE_PUBLISH_GATEWAY.equalsIgnoreCase(deviceType)) {
            return "publish-gateway-" + lastOctet;
        }
        if (TYPE_TERMINAL_GATEWAY.equalsIgnoreCase(deviceType)) {
            return "terminal-gateway-" + lastOctet;
        }
        return null;
    }

    private boolean isRoutableMqttGatewayId(String deviceId) {
        String value = normalizeText(deviceId);
        return value != null
                && (value.startsWith("publish-gateway-") || value.startsWith("terminal-gateway-"));
    }

    private String firstText(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            String text = normalizeText(value == null ? null : String.valueOf(value));
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 通过 MQTT 下发配置命令到发布网关。
     * <p>
     * 发布网关收到 PROXY_COMMAND 后，按 actions 列表执行：
     * SELF_APPLY 处理自身配置，HTTP 转发到终端网关/客户端。
     * </p>
     *
     * @param gatewayDeviceId 发布网关设备 ID
     * @param command        命令名称（如 CHAIN_DEPLOY）
     * @param actions        动作列表
     * @return true 表示 MQTT 发布成功
     */
    private boolean dispatchViaMqtt(String gatewayDeviceId, String command,
                                    List<MqttCommandMessage.Action> actions) {
        if (!mqttCommandPublishService.isMqttEnabled()) {
            log.warn("[MQTT下发] MQTT 未启用，跳过: gatewayDeviceId={}", gatewayDeviceId);
            return false;
        }
        try {
            DeviceMqttCommand mqttCommand = mqttCommandPublishService.publishCommand(gatewayDeviceId, command, null, actions);
            if (mqttCommand == null || !DeviceMqttCommand.STATUS_PUBLISHED.equals(mqttCommand.getStatus())) {
                log.error("[MQTT下发] 命令发布失败: gatewayDeviceId={}, command={}", gatewayDeviceId, command);
                return false;
            }
            log.info("[MQTT下发] 命令已发布: gatewayDeviceId={}, command={}, actions={}, messageId={}",
                    gatewayDeviceId, command, actions != null ? actions.size() : 0, mqttCommand.getMessageId());

            if (shouldUseHttp()) {
                // dual 模式以 HTTP 同步结果为准，MQTT 仅作为灰度旁路。
                return true;
            }

            DeviceMqttCommand finalCommand = mqttCommandPublishService.waitForFinalStatus(
                    mqttCommand.getId(), mqttDispatchProperties.getCommandTimeoutSec());
            boolean success = mqttCommandPublishService.isSuccess(finalCommand);
            if (!success) {
                log.error("[MQTT下发] 命令执行失败或超时: gatewayDeviceId={}, command={}, messageId={}, status={}, error={}",
                        gatewayDeviceId, command, mqttCommand.getMessageId(),
                        finalCommand == null ? null : finalCommand.getStatus(),
                        finalCommand == null ? null : finalCommand.getErrorMessage());
                return false;
            }
            log.info("[MQTT下发] 命令执行成功: gatewayDeviceId={}, command={}, messageId={}",
                    gatewayDeviceId, command, mqttCommand.getMessageId());
            return true;
        } catch (Exception e) {
            log.error("[MQTT下发] 发布失败: gatewayDeviceId={}, error={}", gatewayDeviceId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 执行下发到发布网关
     */
    private boolean doDeployToPublishGateway(Long chainId, String branchCode,
                                             TaskChainNode publishGwNode,
                                             TaskChainNode terminalGwNode,
                                             TaskChainNode infoBoardNode,
                                             TaskChainNode publishServerNode,
                                             int terminalGatewayPort,
                                             String manufacturer,
                                             String publishGatewayMac) {
        try {
            // 获取发布网关地址
            String publishGatewayIp = StrUtil.isNotBlank(publishGwNode.getDeviceIp()) 
                    ? publishGwNode.getDeviceIp() : "192.168.1.25";
            int publishGatewayPort = 8092;

            // 构建发布网关接收配置的URL
            String gatewayUrl = "http://" + publishGatewayIp + ":" + publishGatewayPort;
            String apiUrl = gatewayUrl + "/udp-proxy/config";

            log.info("【发布网关下发】URL={}, chainId={}, branchCode={}", apiUrl, chainId, branchCode);

            if (restTemplate == null) {
                log.error("【发布网关下发】RestTemplate未配置，无法下发配置");
                return false;
            }

            // 构建请求体（发布网关专用格式）
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("chainId", chainId);
            // branchCode可选，不作为必填项
            if (StrUtil.isNotBlank(branchCode)) {
                requestBody.put("branchCode", branchCode);
            }
            requestBody.put("publishGatewayIp", publishGatewayIp);
            Integer infoBoardPortVal = queryDevicePort(TYPE_INFO_BOARD, infoBoardNode.getDeviceId());
            int actualInfoBoardPort = (infoBoardPortVal != null && infoBoardPortVal > 0) ? infoBoardPortVal : 9520;
            requestBody.put("publishGatewayPort", actualInfoBoardPort);

            // 情报板信息
            requestBody.put("infoBoardIp", StrUtil.isNotBlank(infoBoardNode.getDeviceIp())
                    ? infoBoardNode.getDeviceIp() : "");
            requestBody.put("infoBoardPort", actualInfoBoardPort);
            log.info("【发布网关下发】情报板端口: {} (来源:{})",
                    actualInfoBoardPort, infoBoardPortVal != null ? "device模块" : "默认值");
            
            // 加密配置
            requestBody.put("encryptEnabled", true);
            requestBody.put("terminalGatewayIp", StrUtil.isNotBlank(terminalGwNode.getDeviceIp()) 
                    ? terminalGwNode.getDeviceIp() : "");
            requestBody.put("terminalGatewayPort", terminalGatewayPort);

            // 源IP白名单：将发布服务器（Sigma主机）的IP作为授权白名单下发
            if (publishServerNode != null && StrUtil.isNotBlank(publishServerNode.getDeviceIp())) {
                requestBody.put("sourceIp", publishServerNode.getDeviceIp());
                log.info("【发布网关下发】配置源IP白名单（客户端IP）: {}", publishServerNode.getDeviceIp());
            } else {
                log.warn("【发布网关下发】链路未配置发布服务器节点或IP为空，sourceIp 不设置（不限制来源）");
            }

            // 情报板厂家标识（用于发布网关选择协议解析策略）
            if (StrUtil.isNotBlank(manufacturer)) {
                requestBody.put("manufacturer", manufacturer);
                log.info("【发布网关下发】情报板厂家: {}", manufacturer);
            }

            // 传输协议（nova→TCP，sigma/其他→UDP），让发布网关同时启动对应 Server
            String infoBoardProtocol = queryInfoBoardProtocol(manufacturer);
            if (StrUtil.isNotBlank(infoBoardProtocol)) {
                requestBody.put("protocol", infoBoardProtocol);
                log.info("【发布网关下发】情报板传输协议: {}", infoBoardProtocol);
            }

            // 附加TCP端口（诺瓦多端口通信场景：16606控制 + 16602内容）
            String additionalTcpPorts = queryAdditionalTcpPorts(manufacturer);
            if (StrUtil.isNotBlank(additionalTcpPorts)) {
                requestBody.put("additionalTcpPorts", additionalTcpPorts);
                log.info("【发布网关下发】附加TCP代理端口: {}", additionalTcpPorts);
            }
            String additionalUdpPorts = queryAdditionalUdpPorts(manufacturer);
            if (StrUtil.isNotBlank(additionalUdpPorts)) {
                requestBody.put("additionalUdpPorts", additionalUdpPorts);
                log.info("【发布网关下发】附加UDP代理端口: {}", additionalUdpPorts);
            }

            // 动态端口透明代理（仅 Nova 大屏启用，Sigma 等厂商不下发此字段，不受影响）
            Boolean dynamicPortProxy = queryDynamicPortProxyEnabled(manufacturer);
            if (Boolean.TRUE.equals(dynamicPortProxy)) {
                requestBody.put("dynamicPortProxyEnabled", true);
                requestBody.put("catchAllProxyPort", 19999);
                log.info("【发布网关下发】动态端口透明代理: 已启用, CatchAll端口: 19999");
            }

            // ========== 中转配置：让发布网关转发给 PC 客户端 ==========
            // 发布网关与 PC 机在同一局域网（如 192.168.113.x），管控平台无法直达 PC 机，
            // 由发布网关收到配置后原样中转给 PC 客户端的 /udp-proxy/config 接口。
            if (publishServerNode != null && StrUtil.isNotBlank(publishServerNode.getDeviceIp())) {
                requestBody.put("clientIp", publishServerNode.getDeviceIp());
                // 从 device 模块实时查询设备端口，查不到时回退默认值
                Integer devicePort = queryDevicePort(TYPE_PUBLISH_SERVER, publishServerNode.getDeviceId());
                int clientPort = (devicePort != null && devicePort > 0) ? devicePort : defaultClientPort;
                requestBody.put("clientPort", clientPort);
                log.info("【发布网关下发】中转目标 PC 客户端: {}:{} (来源:{})",
                        publishServerNode.getDeviceIp(), clientPort,
                        devicePort != null ? "device模块" : "默认值");
            }
            // 发布网关MAC（客户端用于ARP绑定，通过中转传递）
            if (StrUtil.isNotBlank(publishGatewayMac)) {
                requestBody.put("gatewayMac", publishGatewayMac);
                log.info("【发布网关下发】中转发布网关MAC: {}", publishGatewayMac);
            }

            // MQTT 模式：通过 MQTT 下发到发布网关，不走 HTTP
            if (shouldUseMqtt() && !shouldUseHttp()) {
                MqttCommandMessage.Action selfAction = new MqttCommandMessage.Action();
                selfAction.setTargetDeviceType(TYPE_PUBLISH_GATEWAY);
                selfAction.setMode("SELF_APPLY");
                selfAction.setBody(new HashMap<>(requestBody));
                return dispatchViaMqtt(resolveMqttGatewayDeviceId(publishGwNode), "CHAIN_DEPLOY",
                        Collections.singletonList(selfAction));
            }
            // dual 模式：同时走 MQTT 和 HTTP
            if (shouldUseMqtt()) {
                MqttCommandMessage.Action selfAction = new MqttCommandMessage.Action();
                selfAction.setTargetDeviceType(TYPE_PUBLISH_GATEWAY);
                selfAction.setMode("SELF_APPLY");
                selfAction.setBody(new HashMap<>(requestBody));
                dispatchViaMqtt(resolveMqttGatewayDeviceId(publishGwNode), "CHAIN_DEPLOY",
                        Collections.singletonList(selfAction));
            }

            // 发送HTTP请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Integer code = (Integer) response.getBody().get("code");
                if (code != null && code == 200) {
                    log.info("【发布网关下发】✅ 配置下发成功: chainId={}, branchCode={}", chainId, branchCode);
                    return true;
                } else {
                    String message = (String) response.getBody().get("message");
                    log.error("【发布网关下发】❌ 配置下发失败: {}", message);
                    return false;
                }
            }

            log.error("【发布网关下发】❌ 配置下发失败: HTTP状态码={}", response.getStatusCode());
            return false;

        } catch (Exception e) {
            log.error("【发布网关下发】❌ 下发配置异常: chainId={}, branchCode={}", chainId, branchCode, e);
            return false;
        }
    }

    /**
     * 执行下发到终端网关
     * @return 实际分配的监听端口，失败返回 -1
     */
    private int doDeployToTerminalGateway(Long chainId, String branchCode,
                                              TaskChainNode publishGwNode,
                                              TaskChainNode terminalGwNode,
                                              TaskChainNode infoBoardNode,
                                              TaskChainNode publishServerNode,
                                              String manufacturer) {
        try {
            // 获取终端网关地址
            String terminalGatewayIp = StrUtil.isNotBlank(terminalGwNode.getDeviceIp())
                    ? terminalGwNode.getDeviceIp() : "192.168.1.26";
            int terminalGatewayPort = 8093; // 终端网关HTTP端口

            // 构建终端网关接收配置的URL
            String gatewayUrl = "http://" + terminalGatewayIp + ":" + terminalGatewayPort;
            String apiUrl = gatewayUrl + "/udp-proxy/config";

            log.info("【终端网关下发】URL={}, chainId={}, branchCode={}", apiUrl, chainId, branchCode);

            if (restTemplate == null) {
                log.error("【终端网关下发】RestTemplate未配置，无法下发配置");
                return -1;
            }

            // 构建请求体（终端网关专用格式）
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("chainId", chainId);
            // 从 device 模块查询情报板实际端口，替换硬编码 9520
            Integer termInfoBoardPortVal = queryDevicePort(TYPE_INFO_BOARD, infoBoardNode.getDeviceId());
            int termActualInfoBoardPort = (termInfoBoardPortVal != null && termInfoBoardPortVal > 0) ? termInfoBoardPortVal : 9520;
            requestBody.put("listenPort", termActualInfoBoardPort);

            // 发布网关信息（数据来源）
            String publishGatewayIp = StrUtil.isNotBlank(publishGwNode.getDeviceIp())
                    ? publishGwNode.getDeviceIp() : "192.168.1.25";
            requestBody.put("publishGatewayIp", publishGatewayIp);
            if (publishGwNode != null && StrUtil.isNotBlank(publishGwNode.getDeviceIp())) {
                requestBody.put("sourceIp", publishGwNode.getDeviceIp());
                log.info("【终端网关下发】配置源IP白名单（加密网关IP）: {}", publishGwNode.getDeviceIp());
            } else {
                log.warn("【终端网关下发】链路未配置发布网关节点或IP为空，sourceIp 不设置（不限制来源）");
            }

            // 情报板信息（最终目标）
            requestBody.put("infoBoardIp", StrUtil.isNotBlank(infoBoardNode.getDeviceIp())
                    ? infoBoardNode.getDeviceIp() : "");
            requestBody.put("infoBoardPort", termActualInfoBoardPort);
            log.info("【终端网关下发】情报板端口: {} (来源:{})",
                    termActualInfoBoardPort, termInfoBoardPortVal != null ? "device模块" : "默认值");
            
            // 解密配置
            requestBody.put("decryptEnabled", true);
            
            // 可选字段
            if (StrUtil.isNotBlank(branchCode)) {
                requestBody.put("branchCode", branchCode);
            }

            // 附加TCP端口（诺瓦多端口通信场景：16606控制 + 16602内容）
            String additionalTcpPorts = queryAdditionalTcpPorts(manufacturer);
            if (StrUtil.isNotBlank(additionalTcpPorts)) {
                requestBody.put("additionalTcpPorts", additionalTcpPorts);
                log.info("【终端网关下发】附加TCP代理端口: {}", additionalTcpPorts);
            }
            String additionalUdpPorts = queryAdditionalUdpPorts(manufacturer);
            if (StrUtil.isNotBlank(additionalUdpPorts)) {
                requestBody.put("additionalUdpPorts", additionalUdpPorts);
                log.info("【终端网关下发】附加UDP代理端口: {}", additionalUdpPorts);
            }

            // 动态端口透明代理（仅 Nova 大屏启用，Sigma 等厂商不下发此字段，不受影响）
            Boolean dynamicPortProxy = queryDynamicPortProxyEnabled(manufacturer);
            if (Boolean.TRUE.equals(dynamicPortProxy)) {
                requestBody.put("dynamicPortProxyEnabled", true);
                requestBody.put("catchAllProxyPort", 19999);
                log.info("【终端网关下发】动态端口透明代理: 已启用, CatchAll端口: 19999");
            }

            // MQTT 模式：通过 MQTT 下发，由发布网关 HTTP 转发到终端网关
            if (shouldUseMqtt() && !shouldUseHttp()) {
                MqttCommandMessage.Action httpAction = new MqttCommandMessage.Action();
                httpAction.setTargetDeviceType(TYPE_TERMINAL_GATEWAY);
                httpAction.setTargetDeviceId(terminalGwNode.getDeviceId());
                httpAction.setTargetIp(terminalGatewayIp);
                httpAction.setTargetPort(terminalGatewayPort);
                httpAction.setMode("HTTP");
                httpAction.setPath("/udp-proxy/config");
                httpAction.setHttpMethod("POST");
                Map<String, Object> mqttBody = new HashMap<>(requestBody);
                mqttBody.put("targetIp", terminalGatewayIp);
                mqttBody.put("targetPort", terminalGatewayPort);
                httpAction.setBody(mqttBody);
                boolean mqttSuccess = dispatchViaMqtt(resolveMqttGatewayDeviceId(publishGwNode), "CHAIN_DEPLOY",
                        Collections.singletonList(httpAction));
                if (!mqttSuccess) {
                    return -1;
                }
                // MQTT 模式下无法同步获取 actualListenPort，使用查询到的端口
                return termActualInfoBoardPort;
            }
            // dual 模式：同时走 MQTT 和 HTTP
            if (shouldUseMqtt()) {
                MqttCommandMessage.Action httpAction = new MqttCommandMessage.Action();
                httpAction.setTargetDeviceType(TYPE_TERMINAL_GATEWAY);
                httpAction.setTargetDeviceId(terminalGwNode.getDeviceId());
                httpAction.setTargetIp(terminalGatewayIp);
                httpAction.setTargetPort(terminalGatewayPort);
                httpAction.setMode("HTTP");
                httpAction.setPath("/udp-proxy/config");
                httpAction.setHttpMethod("POST");
                Map<String, Object> mqttBody = new HashMap<>(requestBody);
                mqttBody.put("targetIp", terminalGatewayIp);
                mqttBody.put("targetPort", terminalGatewayPort);
                httpAction.setBody(mqttBody);
                dispatchViaMqtt(resolveMqttGatewayDeviceId(publishGwNode), "CHAIN_DEPLOY",
                        Collections.singletonList(httpAction));
            }

            // 发送HTTP请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Integer code = (Integer) response.getBody().get("code");
                if (code != null && code == 200) {
                    // 解析终端网关返回的实际监听端口
                    int actualPort = termActualInfoBoardPort; // 以查询到的端口为默认值
                    Object dataObj = response.getBody().get("data");
                    if (dataObj instanceof Map) {
                        Map<?, ?> dataMap = (Map<?, ?>) dataObj;
                        Object portObj = dataMap.get("actualListenPort");
                        if (portObj instanceof Number) {
                            actualPort = ((Number) portObj).intValue();
                        }
                    }
                    log.info("【终端网关下发】✅ 配置下发成功: chainId={}, terminalGatewayIp={}, 实际监听端口={}",
                            chainId, terminalGatewayIp, actualPort);
                    return actualPort;
                } else {
                    String message = (String) response.getBody().get("message");
                    log.error("【终端网关下发】❌ 配置下发失败: {}", message);
                    return -1;
                }
            }

            log.error("【终端网关下发】❌ 配置下发失败: HTTP状态码={}", response.getStatusCode());
            return -1;

        } catch (Exception e) {
            log.error("【终端网关下发】❌ 下发配置异常: terminalGatewayIp={}", 
                    terminalGwNode.getDeviceIp(), e);
            return -1;
        }
    }

    /**
     * 根据厂家标识推断情报板传输协议
     */
    private String queryInfoBoardProtocol(String manufacturer) {
        if (StrUtil.isBlank(manufacturer)) {
            return null;
        }
        if ("nova".equalsIgnoreCase(manufacturer.trim())) {
            return "TCP";
        }






        // sigma / colorlight / 其他厂商默认 UDP，返回 null 让下游使用自身默认值
        return null;
    }

    /**
     * 根据厂家返回附加 TCP 代理端口列表
     *
     * <pre>
     * 诺瓦大屏使用多端口通信架构：
     *   16606 — TLS 认证/控制连接（主端口，由 infoBoardPort 配置）
     *   16602 — 内容发布数据传输（附加端口，由本方法返回）
     * 其他厂商（sigma等）无需附加端口，返回 null。
     * </pre>
     *
     * @param manufacturer 厂家标识
     * @return 逗号分隔的附加端口列表（如 "16602"），无需时返回 null
     */
    private String queryAdditionalTcpPorts(String manufacturer) {
        if (StrUtil.isBlank(manufacturer)) {
            return null;
        }
        if ("nova".equalsIgnoreCase(manufacturer.trim())) {
            return "16602";
        }
        return null;
    }

    private String queryAdditionalUdpPorts(String manufacturer) {
        if (StrUtil.isBlank(manufacturer)) {
            return null;
        }
        if ("nova".equalsIgnoreCase(manufacturer.trim())) {
            return "16601,16611";
        }
        return null;
    }

    /**
     * 根据厂家判断是否启用动态端口透明代理
     *
     * <pre>
     * Nova 大屏通过控制通道协商随机动态端口（如 34431, 45219）进行文件传输，
     * 这些端口无法静态配置，需通过 iptables TPROXY + CatchAll 代理动态捕获。
     *
     * Sigma 等厂商使用固定端口通信，无需动态端口代理，返回 null 不影响。
     * </pre>
     *
     * @param manufacturer 厂家标识
     * @return true=启用动态端口代理（仅 Nova），null=不启用
     */
    private Boolean queryDynamicPortProxyEnabled(String manufacturer) {
        if (StrUtil.isBlank(manufacturer)) {
            return null;
        }
        if ("nova".equalsIgnoreCase(manufacturer.trim())) {
            return Boolean.TRUE;
        }
        return null;
    }

    /**
     * 通过 Feign 查询情报板厂家
     *
     * @param deviceId 设备唯一标识
     * @return 厂家标识，查询失败返回 null（下游默认走 sigma 策略）
     */
    @SuppressWarnings("unchecked")
    private String queryManufacturer(String deviceId) {
        if (StrUtil.isBlank(deviceId)) {
            return null;
        }
        try {
            Map<String, Object> resp = deviceFeignClient.getDeviceDetail("info_board", deviceId);
            if (resp != null && Integer.valueOf(200).equals(resp.get("code"))) {
                Map<String, Object> data = (Map<String, Object>) resp.get("data");
                if (data != null) {
                    return (String) data.get("manufacturer");
                }
            }
        } catch (Exception e) {
            log.warn("查询情报板厂家失败: deviceId={}, error={}", deviceId, e.getMessage());
        }
        return null;
    }

    /**
     * 查找指定类型的子节点
     */
    private TaskChainNode findChildOfType(Long parentId, String deviceType, List<TaskChainNode> allNodes) {
        return allNodes.stream()
                .filter(n -> parentId.equals(n.getParentId()) && deviceType.equals(n.getDeviceType()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 删除链路时通知发布网关和终端网关停止并逻辑删除规则
     * 发布网关和终端网关的 udp_proxy_rule 表均执行逻辑删除（deleted=1）
     */
    @Override
    public Map<String, Object> stopChainRules(Long chainId) {
        log.info("==========================================");
        log.info("  【删除链路】开始通知网关停止规则");
        log.info("  链路ID: {}", chainId);
        log.info("==========================================");

        Map<String, Object> result = new HashMap<>();
        boolean publishSuccess = false;
        boolean terminalSuccess = false;
        String publishMsg = "";
        String terminalMsg = "";

        // 从节点中获取发布网关IP和所有终端网关IP
        // 注意：调用此方法时链路节点可能已被物理删除，需先拿到IP再删除
        LambdaQueryWrapper<TaskChainNode> nodeWrapper = new LambdaQueryWrapper<>();
        nodeWrapper.eq(TaskChainNode::getChainId, chainId);
        List<TaskChainNode> allNodes = chainNodeMapper.selectList(nodeWrapper);

        // 1. 通知发布网关
        TaskChainNode publishGwNode = allNodes.stream()
                .filter(n -> TYPE_PUBLISH_GATEWAY.equals(n.getDeviceType()))
                .findFirst().orElse(null);

        String publishGatewayIp = (publishGwNode != null && StrUtil.isNotBlank(publishGwNode.getDeviceIp()))
                ? publishGwNode.getDeviceIp() : "192.168.1.25";
        int publishGatewayPort = 8092;
        String publishApiUrl = "http://" + publishGatewayIp + ":" + publishGatewayPort + "/udp-proxy/chain/" + chainId;

        try {
            if (restTemplate == null) {
                publishMsg = "RestTemplate未配置";
                log.error("【删除链路】发布网关 RestTemplate 未配置");
            } else {
                log.info("【删除链路】调用发布网关删除接口: {}", publishApiUrl);
                ResponseEntity<Map> publishResp = restTemplate.exchange(
                        publishApiUrl, org.springframework.http.HttpMethod.DELETE, null, Map.class);
                if (publishResp.getStatusCode() == HttpStatus.OK && publishResp.getBody() != null) {
                    Integer code = (Integer) publishResp.getBody().get("code");
                    publishSuccess = (code != null && code == 200);
                    publishMsg = publishSuccess ? "成功" : "失败: " + publishResp.getBody().get("msg");
                } else {
                    publishMsg = "HTTP状态码: " + publishResp.getStatusCode();
                }
                log.info("【删除链路】发布网关处理结果: {}", publishMsg);
            }
        } catch (Exception e) {
            publishMsg = "异常: " + e.getMessage();
            log.error("【删除链路】通知发布网关失败: chainId={}", chainId, e);
        }

        // 2. 通知所有终端网关
        List<TaskChainNode> terminalGwNodes = allNodes.stream()
                .filter(n -> TYPE_TERMINAL_GATEWAY.equals(n.getDeviceType()))
                .collect(Collectors.toList());

        if (terminalGwNodes.isEmpty()) {
            terminalMsg = "无终端网关节点，跳过";
            terminalSuccess = true;
            log.warn("【删除链路】链路 {} 无终端网关节点", chainId);
        } else {
            int terminalOk = 0;
            int terminalFail = 0;
            for (TaskChainNode terminalGwNode : terminalGwNodes) {
                String terminalIp = StrUtil.isNotBlank(terminalGwNode.getDeviceIp())
                        ? terminalGwNode.getDeviceIp() : "192.168.1.26";
                int terminalPort = 8093;
                String terminalApiUrl = "http://" + terminalIp + ":" + terminalPort + "/udp-proxy/chain/" + chainId;
                try {
                    log.info("【删除链路】调用终端网关删除接口: {}", terminalApiUrl);
                    ResponseEntity<Map> terminalResp = restTemplate.exchange(
                            terminalApiUrl, org.springframework.http.HttpMethod.DELETE, null, Map.class);
                    if (terminalResp.getStatusCode() == HttpStatus.OK && terminalResp.getBody() != null) {
                        Integer code = (Integer) terminalResp.getBody().get("code");
                        if (code != null && code == 200) {
                            terminalOk++;
                            log.info("✅ 终端网关 {} 处理成功", terminalIp);
                        } else {
                            terminalFail++;
                            log.error("❌ 终端网关 {} 处理失败: {}", terminalIp, terminalResp.getBody().get("msg"));
                        }
                    } else {
                        terminalFail++;
                        log.error("❌ 终端网关 {} HTTP状态码: {}", terminalIp, terminalResp.getStatusCode());
                    }
                } catch (Exception e) {
                    terminalFail++;
                    log.error("❌ 通知终端网关 {} 失败: {}", terminalIp, e.getMessage());
                }
            }
            terminalSuccess = (terminalFail == 0);
            terminalMsg = String.format("成功: %d, 失败: %d", terminalOk, terminalFail);
        }

        // 3. 通知信息发布客户端
        boolean clientSuccess = false;
        String clientMsg = "";
        TaskChainNode publishServerNode = allNodes.stream()
                .filter(n -> TYPE_PUBLISH_SERVER.equals(n.getDeviceType()))
                .findFirst().orElse(null);

        if (publishServerNode != null && StrUtil.isNotBlank(publishServerNode.getDeviceIp())) {
            Integer deleteDevicePort = queryDevicePort(TYPE_PUBLISH_SERVER, publishServerNode.getDeviceId());
            int deleteClientPort = (deleteDevicePort != null && deleteDevicePort > 0) ? deleteDevicePort : defaultClientPort;
            String clientApiUrl = "http://" + publishServerNode.getDeviceIp() + ":" + deleteClientPort
                    + "/udp-proxy/chain/" + chainId;
            try {
                log.info("【删除链路】调用信息发布客户端删除接口: {} (端口来源:{})", clientApiUrl,
                        deleteDevicePort != null ? "device模块" : "默认值");
                ResponseEntity<Map> clientResp = restTemplate.exchange(
                        clientApiUrl, org.springframework.http.HttpMethod.DELETE, null, Map.class);
                if (clientResp.getStatusCode() == HttpStatus.OK && clientResp.getBody() != null) {
                    Integer code = (Integer) clientResp.getBody().get("code");
                    clientSuccess = (code != null && code == 200);
                    clientMsg = clientSuccess ? "成功" : "失败: " + clientResp.getBody().get("msg");
                } else {
                    clientMsg = "HTTP状态码: " + clientResp.getStatusCode();
                }
                log.info("【删除链路】信息发布客户端处理结果: {}", clientMsg);
            } catch (Exception e) {
                clientMsg = "异常: " + e.getMessage();
                log.error("【删除链路】通知信息发布客户端失败: chainId={}", chainId, e);
            }
        } else {
            clientMsg = "无发布服务器节点，跳过";
            clientSuccess = true;
        }

        result.put("chainId", chainId);
        Map<String, Object> publishGatewayResult = new HashMap<>();
        publishGatewayResult.put("success", publishSuccess);
        publishGatewayResult.put("msg", publishMsg);
        result.put("publishGateway", publishGatewayResult);

        Map<String, Object> terminalGatewayResult = new HashMap<>();
        terminalGatewayResult.put("success", terminalSuccess);
        terminalGatewayResult.put("msg", terminalMsg);
        result.put("terminalGateway", terminalGatewayResult);

        Map<String, Object> publishClientResult = new HashMap<>();
        publishClientResult.put("success", clientSuccess);
        publishClientResult.put("msg", clientMsg);
        result.put("publishClient", publishClientResult);
        log.info("【删除链路】网关通知完毕: 发布网关={}, 终端网关={}, 发布客户端={}", publishMsg, terminalMsg, clientMsg);
        return result;
    }

    @Override
    public Map<String, Object> setChainEnabled(Long chainId, Integer enabled) {
        String action = (enabled == 1) ? "启用" : "停用";
        String gatewayStatus = (enabled == 1) ? "ENABLE" : "DISABLE";
        log.info("==========================================");
        log.info("  【{}链路】开始通知网关切换规则状态", action);
        log.info("  链路ID: {}, 网关目标状态: {}", chainId, gatewayStatus);
        log.info("==========================================");

        Map<String, Object> result = new HashMap<>();
        boolean publishSuccess = false;
        boolean terminalSuccess = false;
        String publishMsg = "";
        String terminalMsg = "";

        // 获取链路节点（取得网关IP）
        LambdaQueryWrapper<TaskChainNode> nodeWrapper = new LambdaQueryWrapper<>();
        nodeWrapper.eq(TaskChainNode::getChainId, chainId);
        List<TaskChainNode> allNodes = chainNodeMapper.selectList(nodeWrapper);

        // 1. 通知发布网关
        TaskChainNode publishGwNode = allNodes.stream()
                .filter(n -> TYPE_PUBLISH_GATEWAY.equals(n.getDeviceType()))
                .findFirst().orElse(null);
        String publishGatewayIp = (publishGwNode != null && StrUtil.isNotBlank(publishGwNode.getDeviceIp()))
                ? publishGwNode.getDeviceIp() : "192.168.1.25";
        String publishApiUrl = "http://" + publishGatewayIp + ":8092/udp-proxy/chain/" + chainId + "/status?status=" + gatewayStatus;
        try {
            if (restTemplate == null) {
                publishMsg = "RestTemplate未配置";
            } else {
                log.info("【{}链路】调用发布网关: {}", action, publishApiUrl);
                ResponseEntity<Map> resp = restTemplate.exchange(
                        publishApiUrl, org.springframework.http.HttpMethod.PUT, null, Map.class);
                if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null) {
                    Integer code = (Integer) resp.getBody().get("code");
                    publishSuccess = (code != null && code == 200);
                    publishMsg = publishSuccess ? "成功" : "失败: " + resp.getBody().get("msg");
                } else {
                    publishMsg = "HTTP状态码: " + resp.getStatusCode();
                }
                log.info("【{}链路】发布网关处理结果: {}", action, publishMsg);
            }
        } catch (Exception e) {
            publishMsg = "异常: " + e.getMessage();
            log.error("【{}链路】通知发布网关失败: chainId={}", action, chainId, e);
        }

        // 2. 通知所有终端网关
        List<TaskChainNode> terminalGwNodes = allNodes.stream()
                .filter(n -> TYPE_TERMINAL_GATEWAY.equals(n.getDeviceType()))
                .collect(Collectors.toList());
        if (terminalGwNodes.isEmpty()) {
            terminalMsg = "无终端网关节点，跳过";
            terminalSuccess = true;
        } else {
            int ok = 0, fail = 0;
            for (TaskChainNode terminalGwNode : terminalGwNodes) {
                String terminalIp = StrUtil.isNotBlank(terminalGwNode.getDeviceIp())
                        ? terminalGwNode.getDeviceIp() : "192.168.1.26";
                String terminalApiUrl = "http://" + terminalIp + ":8093/udp-proxy/chain/" + chainId + "/status?status=" + gatewayStatus;
                try {
                    log.info("【{}链路】调用终端网关: {}", action, terminalApiUrl);
                    ResponseEntity<Map> resp = restTemplate.exchange(
                            terminalApiUrl, org.springframework.http.HttpMethod.PUT, null, Map.class);
                    if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null) {
                        Integer code = (Integer) resp.getBody().get("code");
                        if (code != null && code == 200) { ok++; } else { fail++; }
                    } else { fail++; }
                } catch (Exception e) {
                    fail++;
                    log.error("❌ 通知终端网关 {} 失败: {}", terminalIp, e.getMessage());
                }
            }
            terminalSuccess = (fail == 0);
            terminalMsg = String.format("成功: %d, 失败: %d", ok, fail);
        }

        // 3. 通知信息发布客户端
        boolean clientSuccess = false;
        String clientMsg = "";
        TaskChainNode publishServerNode = allNodes.stream()
                .filter(n -> TYPE_PUBLISH_SERVER.equals(n.getDeviceType()))
                .findFirst().orElse(null);

        if (publishServerNode != null && StrUtil.isNotBlank(publishServerNode.getDeviceIp())) {
            Integer statusDevicePort = queryDevicePort(TYPE_PUBLISH_SERVER, publishServerNode.getDeviceId());
            int statusClientPort = (statusDevicePort != null && statusDevicePort > 0) ? statusDevicePort : defaultClientPort;
            String clientApiUrl = "http://" + publishServerNode.getDeviceIp() + ":" + statusClientPort
                    + "/udp-proxy/chain/" + chainId + "/status?status=" + gatewayStatus;
            try {
                log.info("【{}链路】调用信息发布客户端: {} (端口来源:{})", action, clientApiUrl,
                        statusDevicePort != null ? "device模块" : "默认值");
                ResponseEntity<Map> resp = restTemplate.exchange(
                        clientApiUrl, org.springframework.http.HttpMethod.PUT, null, Map.class);
                if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null) {
                    Integer code = (Integer) resp.getBody().get("code");
                    clientSuccess = (code != null && code == 200);
                    clientMsg = clientSuccess ? "成功" : "失败: " + resp.getBody().get("msg");
                } else {
                    clientMsg = "HTTP状态码: " + resp.getStatusCode();
                }
                log.info("【{}链路】信息发布客户端处理结果: {}", action, clientMsg);
            } catch (Exception e) {
                clientMsg = "异常: " + e.getMessage();
                log.error("【{}链路】通知信息发布客户端失败: chainId={}", action, chainId, e);
            }
        } else {
            clientMsg = "无发布服务器节点，跳过";
            clientSuccess = true;
        }

        result.put("chainId", chainId);
        Map<String, Object> publishGatewayResult = new HashMap<>();
        publishGatewayResult.put("success", publishSuccess);
        publishGatewayResult.put("msg", publishMsg);
        result.put("publishGateway", publishGatewayResult);
        Map<String, Object> terminalGatewayResult = new HashMap<>();
        terminalGatewayResult.put("success", terminalSuccess);
        terminalGatewayResult.put("msg", terminalMsg);
        result.put("terminalGateway", terminalGatewayResult);

        Map<String, Object> publishClientResult = new HashMap<>();
        publishClientResult.put("success", clientSuccess);
        publishClientResult.put("msg", clientMsg);
        result.put("publishClient", publishClientResult);
        log.info("【{}链路】网关通知完毕: 发布网关={}, 终端网关={}, 发布客户端={}", action, publishMsg, terminalMsg, clientMsg);
        return result;
    }

    /**
     * 执行下发到信息发布客户端（publish_server/Sigma主机）
     * 客户端接口与发布网关完全相同，额外增加 gatewayMac 字段用于 ARP 绑定
     */
    private boolean doDeployToPublishClient(Long chainId, String branchCode,
                                             TaskChainNode publishGwNode,
                                             TaskChainNode terminalGwNode,
                                             TaskChainNode infoBoardNode,
                                             TaskChainNode publishServerNode,
                                             int terminalGatewayPort,
                                             String manufacturer,
                                             String publishGatewayMac) {
        try {
            String clientIp = publishServerNode.getDeviceIp();
            // 从 device 模块实时查询设备端口，查不到时回退默认值
            Integer devicePort = queryDevicePort(TYPE_PUBLISH_SERVER, publishServerNode.getDeviceId());
            int clientPort = (devicePort != null && devicePort > 0) ? devicePort : defaultClientPort;
            String apiUrl = "http://" + clientIp + ":" + clientPort + "/udp-proxy/config";

            log.info("【客户端下发】URL={}, chainId={}, branchCode={}", apiUrl, chainId, branchCode);

            if (restTemplate == null) {
                log.error("【客户端下发】RestTemplate未配置，无法下发配置");
                return false;
            }

            // 构建请求体（与发布网关相同格式，额外增加 gatewayMac）
            String publishGatewayIp = StrUtil.isNotBlank(publishGwNode.getDeviceIp())
                    ? publishGwNode.getDeviceIp() : "192.168.1.25";

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("chainId", chainId);
            if (StrUtil.isNotBlank(branchCode)) {
                requestBody.put("branchCode", branchCode);
            }
            requestBody.put("publishGatewayIp", publishGatewayIp);
            // 从 device 模块查询情报板实际端口，替换硬编码 9520
            Integer clientInfoBoardPortVal = queryDevicePort(TYPE_INFO_BOARD, infoBoardNode.getDeviceId());
            int clientActualInfoBoardPort = (clientInfoBoardPortVal != null && clientInfoBoardPortVal > 0) ? clientInfoBoardPortVal : 9520;
            requestBody.put("publishGatewayPort", clientActualInfoBoardPort);
            requestBody.put("infoBoardIp", StrUtil.isNotBlank(infoBoardNode.getDeviceIp())
                    ? infoBoardNode.getDeviceIp() : "");
            requestBody.put("infoBoardPort", clientActualInfoBoardPort);
            log.info("【客户端下发】情报板端口: {} (来源:{})",
                    clientActualInfoBoardPort, clientInfoBoardPortVal != null ? "device模块" : "默认值");
            requestBody.put("encryptEnabled", true);
            requestBody.put("terminalGatewayIp", StrUtil.isNotBlank(terminalGwNode.getDeviceIp())
                    ? terminalGwNode.getDeviceIp() : "");
            requestBody.put("terminalGatewayPort", terminalGatewayPort);

            // 源IP白名单
            if (StrUtil.isNotBlank(publishServerNode.getDeviceIp())) {
                requestBody.put("sourceIp", publishServerNode.getDeviceIp());
            }

            // 情报板厂家
            if (StrUtil.isNotBlank(manufacturer)) {
                requestBody.put("manufacturer", manufacturer);
            }

            // 传输协议（nova→TCP，sigma/其他→UDP）
            String clientProtocol = queryInfoBoardProtocol(manufacturer);
            if (StrUtil.isNotBlank(clientProtocol)) {
                requestBody.put("protocol", clientProtocol);
                log.info("【客户端下发】情报板传输协议: {}", clientProtocol);
            }

            // 客户端特有：发布网关MAC地址（用于ARP绑定）
            if (StrUtil.isNotBlank(publishGatewayMac)) {
                requestBody.put("gatewayMac", publishGatewayMac);
                log.info("【客户端下发】发布网关MAC: {}", publishGatewayMac);
            } else {
                log.warn("【客户端下发】未获取到发布网关MAC，客户端将无法执行ARP绑定");
            }

            // MQTT 模式：通过 MQTT 下发，由发布网关 HTTP 转发到 PC 客户端
            if (shouldUseMqtt() && !shouldUseHttp()) {
                MqttCommandMessage.Action httpAction = new MqttCommandMessage.Action();
                httpAction.setTargetDeviceType(TYPE_PUBLISH_SERVER);
                httpAction.setTargetDeviceId(publishServerNode.getDeviceId());
                httpAction.setTargetIp(clientIp);
                httpAction.setTargetPort(clientPort);
                httpAction.setMode("HTTP");
                httpAction.setPath("/udp-proxy/config");
                httpAction.setHttpMethod("POST");
                Map<String, Object> mqttBody = new HashMap<>(requestBody);
                mqttBody.put("targetIp", clientIp);
                mqttBody.put("targetPort", clientPort);
                httpAction.setBody(mqttBody);
                return dispatchViaMqtt(resolveMqttGatewayDeviceId(publishGwNode), "CHAIN_DEPLOY",
                        Collections.singletonList(httpAction));
            }
            // dual 模式：同时走 MQTT 和 HTTP
            if (shouldUseMqtt()) {
                MqttCommandMessage.Action httpAction = new MqttCommandMessage.Action();
                httpAction.setTargetDeviceType(TYPE_PUBLISH_SERVER);
                httpAction.setTargetDeviceId(publishServerNode.getDeviceId());
                httpAction.setTargetIp(clientIp);
                httpAction.setTargetPort(clientPort);
                httpAction.setMode("HTTP");
                httpAction.setPath("/udp-proxy/config");
                httpAction.setHttpMethod("POST");
                Map<String, Object> mqttBody = new HashMap<>(requestBody);
                mqttBody.put("targetIp", clientIp);
                mqttBody.put("targetPort", clientPort);
                httpAction.setBody(mqttBody);
                dispatchViaMqtt(resolveMqttGatewayDeviceId(publishGwNode), "CHAIN_DEPLOY",
                        Collections.singletonList(httpAction));
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Integer code = (Integer) response.getBody().get("code");
                if (code != null && code == 200) {
                    log.info("【客户端下发】✅ 配置下发成功: chainId={}, branchCode={}", chainId, branchCode);
                    return true;
                } else {
                    String message = (String) response.getBody().get("message");
                    log.error("【客户端下发】❌ 配置下发失败: {}", message);
                    return false;
                }
            }

            log.error("【客户端下发】❌ 配置下发失败: HTTP状态码={}", response.getStatusCode());
            return false;

        } catch (Exception e) {
            log.error("【客户端下发】❌ 下发配置异常: clientIp={}", publishServerNode.getDeviceIp(), e);
            return false;
        }
    }

    /**
     * 通过 Feign 查询设备MAC地址
     *
     * @param deviceId 设备唯一标识
     * @return MAC地址，查询失败返回 null
     */
    @SuppressWarnings("unchecked")
    private String queryDeviceMac(String deviceId) {
        if (StrUtil.isBlank(deviceId)) {
            return null;
        }
        try {
            Map<String, Object> resp = deviceFeignClient.getDeviceDetail("publish_gateway", deviceId);
            if (resp != null && Integer.valueOf(200).equals(resp.get("code"))) {
                Map<String, Object> data = (Map<String, Object>) resp.get("data");
                if (data != null) {
                    return (String) data.get("mac");
                }
            }
        } catch (Exception e) {
            log.warn("查询设备MAC失败: deviceId={}, error={}", deviceId, e.getMessage());
        }
        return null;
    }

    /**
     * 从 device 模块查询设备端口（兜底，当 TaskChainNode.devicePort 为空时使用）
     *
     * @param deviceType 设备类型
     * @param deviceId   设备唯一标识
     * @return 端口号，查询失败返回 null
     */
    @SuppressWarnings("unchecked")
    private Integer queryDevicePort(String deviceType, String deviceId) {
        if (StrUtil.isBlank(deviceId)) {
            return null;
        }
        try {
            Map<String, Object> resp = deviceFeignClient.getDeviceDetail(deviceType, deviceId);
            if (resp != null && Integer.valueOf(200).equals(resp.get("code"))) {
                Map<String, Object> data = (Map<String, Object>) resp.get("data");
                if (data != null && data.get("port") != null) {
                    return Integer.valueOf(data.get("port").toString());
                }
            }
        } catch (Exception e) {
            log.warn("查询设备端口失败: deviceType={}, deviceId={}, error={}", deviceType, deviceId, e.getMessage());
        }
        return null;
    }
}



