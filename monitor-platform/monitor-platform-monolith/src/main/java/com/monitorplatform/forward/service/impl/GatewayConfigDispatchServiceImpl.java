//package com.monitorplatform.forward.service.impl;
//
//import cn.hutool.core.collection.CollUtil;
//import cn.hutool.core.util.StrUtil;
//import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.monitorplatform.forward.entity.GatewayDispatchLog;
//import com.monitorplatform.forward.entity.TaskChainConfig;
//import com.monitorplatform.forward.entity.TaskChainNode;
//import com.monitorplatform.forward.entity.dto.GatewayBranchConfigDTO;
//import com.monitorplatform.forward.mapper.GatewayDispatchLogMapper;
//import com.monitorplatform.forward.mapper.TaskChainConfigMapper;
//import com.monitorplatform.forward.mapper.TaskChainNodeMapper;
//import com.monitorplatform.forward.service.GatewayConfigDispatchService;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.http.HttpEntity;
//import org.springframework.http.HttpHeaders;
//import org.springframework.http.MediaType;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//import org.springframework.web.client.RestTemplate;
//
//import javax.annotation.Resource;
//import java.time.LocalDateTime;
//import java.util.*;
//import java.util.stream.Collectors;
//
///**
// * 网关配置下发服务实现类
// */
//@Slf4j
//@Service
//public class GatewayConfigDispatchServiceImpl implements GatewayConfigDispatchService {
//
//    @Resource
//    private GatewayDispatchLogMapper dispatchLogMapper;
//
//    @Resource
//    private TaskChainConfigMapper chainConfigMapper;
//
//    @Resource
//    private TaskChainNodeMapper chainNodeMapper;
//
//    @Autowired(required = false)
//    private RestTemplate restTemplate;
//
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    /**
//     * 是否启用自动下发（配置保存后自动下发）
//     */
//    @Value("${gateway.dispatch.auto-dispatch:true}")
//    private boolean autoDispatch;
//
//    /**
//     * 网关接收配置的端口（默认9001）
//     */
//    @Value("${gateway.dispatch.port:9001}")
//    private int gatewayPort;
//
//    /**
//     * 下发超时时间（毫秒）
//     */
//    @Value("${gateway.dispatch.timeout:5000}")
//    private int dispatchTimeout;
//
//    // 设备类型常量
//    private static final String TYPE_PUBLISH_GATEWAY = "publish_gateway";
//    private static final String TYPE_TERMINAL_GATEWAY = "terminal_encrypt_gateway";
//    private static final String TYPE_INFO_BOARD = "info_board";
//
//    @Override
//    @Transactional(rollbackFor = Exception.class)
//    public List<GatewayDispatchLog> dispatchChainConfig(Long chainId) {
//        log.info("开始下发链路配置: chainId={}", chainId);
//
//        // 1. 查询链路配置
//        TaskChainConfig config = chainConfigMapper.selectById(chainId);
//        if (config == null || config.getDeleted() == 1) {
//            throw new RuntimeException("链路不存在或已删除");
//        }
//
//        // 2. 查询所有节点
//        LambdaQueryWrapper<TaskChainNode> wrapper = new LambdaQueryWrapper<>();
//        wrapper.eq(TaskChainNode::getChainId, chainId);
//        List<TaskChainNode> allNodes = chainNodeMapper.selectList(wrapper);
//
//        if (CollUtil.isEmpty(allNodes)) {
//            throw new RuntimeException("链路没有任何节点");
//        }
//
//        // 3. 查找发布加密网关节点
//        TaskChainNode gatewayNode = allNodes.stream()
//                .filter(n -> TYPE_PUBLISH_GATEWAY.equals(n.getDeviceType()))
//                .findFirst()
//                .orElseThrow(() -> new RuntimeException("链路缺少发布加密网关节点"));
//
//        // 4. 查找所有终端加密网关（分支起点）
//        List<TaskChainNode> terminalGateways = allNodes.stream()
//                .filter(n -> TYPE_TERMINAL_GATEWAY.equals(n.getDeviceType()))
//                .collect(Collectors.toList());
//
//        if (CollUtil.isEmpty(terminalGateways)) {
//            throw new RuntimeException("链路缺少终端加密网关节点");
//        }
//
//        // 5. 为每个分支构建配置并下发
//        List<GatewayDispatchLog> dispatchLogs = new ArrayList<>();
//        int branchIndex = 1;
//
//        for (TaskChainNode terminalGw : terminalGateways) {
//            // 5.1 查找该终端网关下的情报板
//            TaskChainNode infoBoard = findChildOfType(terminalGw.getId(), TYPE_INFO_BOARD, allNodes);
//            if (infoBoard == null) {
//                log.warn("终端加密网关[{}]下未找到情报板，跳过", terminalGw.getDeviceId());
//                continue;
//            }
//
//            // 5.2 构建分支配置DTO
//            String branchCode = StrUtil.isNotBlank(terminalGw.getBranchCode())
//                    ? terminalGw.getBranchCode()
//                    : "B" + branchIndex++;
//
//            GatewayBranchConfigDTO configDTO = buildBranchConfig(
//                    config, gatewayNode, terminalGw, infoBoard, branchCode
//            );
//
//            // 5.3 创建下发记录
//            GatewayDispatchLog dispatchLog = createDispatchLog(config, gatewayNode, branchCode, configDTO);
//            dispatchLogMapper.insert(dispatchLog);
//
//            // 5.4 执行下发
//            boolean success = doDispatch(dispatchLog, configDTO);
//
//            // 5.5 更新下发状态
//            if (success) {
//                dispatchLog.setDispatchStatus(GatewayDispatchLog.STATUS_DISPATCHED);
//            } else {
//                dispatchLog.setDispatchStatus(GatewayDispatchLog.STATUS_FAILED);
//                dispatchLog.setNextRetryTime(LocalDateTime.now().plusMinutes(1));
//            }
//            dispatchLog.setDispatchTime(LocalDateTime.now());
//            dispatchLogMapper.updateById(dispatchLog);
//
//            dispatchLogs.add(dispatchLog);
//        }
//
//        log.info("链路配置下发完成: chainId={}, 下发分支数={}", chainId, dispatchLogs.size());
//        return dispatchLogs;
//    }
//
//    /**
//     * 构建分支配置DTO
//     */
//    private GatewayBranchConfigDTO buildBranchConfig(TaskChainConfig config,
//                                                      TaskChainNode gatewayNode,
//                                                      TaskChainNode terminalGw,
//                                                      TaskChainNode infoBoard,
//                                                      String branchCode) {
//        GatewayBranchConfigDTO dto = new GatewayBranchConfigDTO();
//
//        // 链路标识
//        dto.setChainId(config.getId());
//        dto.setChainCode(config.getChainCode());
//        dto.setConfigVersion(config.getVersion());
//
//        // 发布加密网关信息（从nodeConfig中提取IP/端口，或使用默认值）
//        dto.setPublishGatewayId(gatewayNode.getDeviceId());
//        Map<String, Object> gwConfig = parseNodeConfig(gatewayNode.getNodeConfig());
//        dto.setPublishGatewayIp((String) gwConfig.getOrDefault("ip", ""));
//        dto.setPublishGatewayPort((Integer) gwConfig.getOrDefault("port", gatewayPort));
//
//        // 分支标识
//        dto.setBranchCode(branchCode);
//        dto.setBranchName(terminalGw.getBranchName());
//
//        // ★核心：终端加密网关IP
//        Map<String, Object> terminalConfig = parseNodeConfig(terminalGw.getNodeConfig());
//        dto.setTerminalGatewayIp((String) terminalConfig.getOrDefault("ip", ""));
//        dto.setTerminalGatewayPort((Integer) terminalConfig.getOrDefault("port", 9002));
//
//        // ★核心：情报板IP
//        Map<String, Object> boardConfig = parseNodeConfig(infoBoard.getNodeConfig());
//        dto.setInfoBoardIp((String) boardConfig.getOrDefault("ip", ""));
//        dto.setInfoBoardPort((Integer) boardConfig.getOrDefault("port", 10001));
//
//        // 时间戳
//        dto.setTimestamp(System.currentTimeMillis());
//
//        return dto;
//    }
//
//    /**
//     * 创建下发记录
//     */
//    private GatewayDispatchLog createDispatchLog(TaskChainConfig config,
//                                                  TaskChainNode gatewayNode,
//                                                  String branchCode,
//                                                  GatewayBranchConfigDTO configDTO) {
//        GatewayDispatchLog log = new GatewayDispatchLog();
//        log.setChainId(config.getId());
//        log.setChainCode(config.getChainCode());
//        log.setBranchCode(branchCode);
//        log.setGatewayDeviceId(gatewayNode.getDeviceId());
//        log.setGatewayIp(configDTO.getPublishGatewayIp());
//        log.setGatewayPort(configDTO.getPublishGatewayPort());
//        log.setConfigVersion(config.getVersion());
//        log.setDispatchStatus(GatewayDispatchLog.STATUS_PENDING);
//        log.setRetryCount(0);
//        log.setMaxRetry(3);
//        log.setCreateTime(LocalDateTime.now());
//
//        try {
//            log.setDispatchContent(objectMapper.writeValueAsString(configDTO));
//        } catch (Exception e) {
//            log.setDispatchContent("{}");
//        }
//
//        return log;
//    }
//
//    /**
//     * 执行下发（HTTP POST）
//     */
//    private boolean doDispatch(GatewayDispatchLog dispatchLog, GatewayBranchConfigDTO configDTO) {
//        if (restTemplate == null) {
//            log.warn("RestTemplate未配置，模拟下发成功");
//            return true;
//        }
//
//        String gatewayIp = dispatchLog.getGatewayIp();
//        if (StrUtil.isBlank(gatewayIp)) {
//            log.error("网关IP为空，无法下发: logId={}", dispatchLog.getId());
//            return false;
//        }
//
//        try {
//            String url = String.format("http://%s:%d/api/gateway/config/receive",
//                    gatewayIp, dispatchLog.getGatewayPort());
//
//            HttpHeaders headers = new HttpHeaders();
//            headers.setContentType(MediaType.APPLICATION_JSON);
//            HttpEntity<GatewayBranchConfigDTO> request = new HttpEntity<>(configDTO, headers);
//
//            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
//
//            if (response != null && "OK".equals(response.get("code"))) {
//                log.info("配置下发成功: logId={}, gateway={}", dispatchLog.getId(), gatewayIp);
//                return true;
//            } else {
//                log.warn("网关返回异常: logId={}, response={}", dispatchLog.getId(), response);
//                return false;
//            }
//        } catch (Exception e) {
//            log.error("配置下发失败: logId={}, error={}", dispatchLog.getId(), e.getMessage());
//            return false;
//        }
//    }
//
//    @Override
//    @Transactional(rollbackFor = Exception.class)
//    public Boolean retryDispatch(Long logId) {
//        log.info("重试下发: logId={}", logId);
//
//        GatewayDispatchLog dispatchLog = dispatchLogMapper.selectById(logId);
//        if (dispatchLog == null) {
//            throw new RuntimeException("下发记录不存在");
//        }
//
//        if (dispatchLog.getRetryCount() >= dispatchLog.getMaxRetry()) {
//            log.warn("已达最大重试次数: logId={}", logId);
//            return false;
//        }
//
//        // 解析配置内容
//        GatewayBranchConfigDTO configDTO;
//        try {
//            configDTO = objectMapper.readValue(dispatchLog.getDispatchContent(), GatewayBranchConfigDTO.class);
//        } catch (Exception e) {
//            log.error("解析配置内容失败: logId={}", logId, e);
//            return false;
//        }
//
//        // 重新下发
//        boolean success = doDispatch(dispatchLog, configDTO);
//
//        // 更新状态
//        dispatchLog.setRetryCount(dispatchLog.getRetryCount() + 1);
//        if (success) {
//            dispatchLog.setDispatchStatus(GatewayDispatchLog.STATUS_DISPATCHED);
//        } else {
//            dispatchLog.setDispatchStatus(GatewayDispatchLog.STATUS_FAILED);
//            dispatchLog.setNextRetryTime(LocalDateTime.now().plusMinutes(
//                    (long) Math.pow(2, dispatchLog.getRetryCount()))); // 指数退避
//        }
//        dispatchLog.setDispatchTime(LocalDateTime.now());
//        dispatchLog.setUpdateTime(LocalDateTime.now());
//        dispatchLogMapper.updateById(dispatchLog);
//
//        return success;
//    }
//
//    @Override
//    @Transactional(rollbackFor = Exception.class)
//    public Boolean handleAck(Long logId, String ackCode, String ackMessage) {
//        log.info("处理ACK回执: logId={}, ackCode={}", logId, ackCode);
//
//        GatewayDispatchLog dispatchLog = dispatchLogMapper.selectById(logId);
//        if (dispatchLog == null) {
//            throw new RuntimeException("下发记录不存在");
//        }
//
//        dispatchLog.setAckCode(ackCode);
//        dispatchLog.setAckMessage(ackMessage);
//        dispatchLog.setAckTime(LocalDateTime.now());
//
//        if ("OK".equals(ackCode) || "SUCCESS".equals(ackCode)) {
//            dispatchLog.setDispatchStatus(GatewayDispatchLog.STATUS_CONFIRMED);
//        } else {
//            dispatchLog.setDispatchStatus(GatewayDispatchLog.STATUS_FAILED);
//        }
//
//        dispatchLog.setUpdateTime(LocalDateTime.now());
//        dispatchLogMapper.updateById(dispatchLog);
//
//        return true;
//    }
//
//    @Override
//    public List<GatewayBranchConfigDTO> pullConfigByGateway(String gatewayDeviceId) {
//        log.info("网关拉取配置: gatewayDeviceId={}", gatewayDeviceId);
//
//        // 1. 查找该网关关联的所有链路节点
//        LambdaQueryWrapper<TaskChainNode> nodeWrapper = new LambdaQueryWrapper<>();
//        nodeWrapper.eq(TaskChainNode::getDeviceId, gatewayDeviceId)
//                .eq(TaskChainNode::getDeviceType, TYPE_PUBLISH_GATEWAY);
//        List<TaskChainNode> gatewayNodes = chainNodeMapper.selectList(nodeWrapper);
//
//        if (CollUtil.isEmpty(gatewayNodes)) {
//            log.warn("未找到网关关联的链路: gatewayDeviceId={}", gatewayDeviceId);
//            return new ArrayList<>();
//        }
//
//        // 2. 获取所有关联链路的最新配置
//        List<GatewayBranchConfigDTO> configs = new ArrayList<>();
//
//        for (TaskChainNode gwNode : gatewayNodes) {
//            Long chainId = gwNode.getChainId();
//
//            // 查询该链路的最新下发记录
//            LambdaQueryWrapper<GatewayDispatchLog> logWrapper = new LambdaQueryWrapper<>();
//            logWrapper.eq(GatewayDispatchLog::getChainId, chainId)
//                    .eq(GatewayDispatchLog::getGatewayDeviceId, gatewayDeviceId)
//                    .orderByDesc(GatewayDispatchLog::getConfigVersion);
//            List<GatewayDispatchLog> logs = dispatchLogMapper.selectList(logWrapper);
//
//            for (GatewayDispatchLog dispatchLog : logs) {
//                try {
//                    GatewayBranchConfigDTO dto = objectMapper.readValue(
//                            dispatchLog.getDispatchContent(), GatewayBranchConfigDTO.class);
//                    configs.add(dto);
//                } catch (Exception e) {
//                    log.error("解析配置失败: logId={}", dispatchLog.getId(), e);
//                }
//            }
//        }
//
//        return configs;
//    }
//
//    @Override
//    public List<GatewayDispatchLog> queryDispatchStatus(Long chainId) {
//        LambdaQueryWrapper<GatewayDispatchLog> wrapper = new LambdaQueryWrapper<>();
//        wrapper.eq(GatewayDispatchLog::getChainId, chainId)
//                .orderByDesc(GatewayDispatchLog::getCreateTime);
//        return dispatchLogMapper.selectList(wrapper);
//    }
//
//    @Override
//    @Transactional(rollbackFor = Exception.class)
//    public Integer retryAllFailed() {
//        log.info("批量重发失败的配置");
//
//        LambdaQueryWrapper<GatewayDispatchLog> wrapper = new LambdaQueryWrapper<>();
//        wrapper.eq(GatewayDispatchLog::getDispatchStatus, GatewayDispatchLog.STATUS_FAILED)
//                .lt(GatewayDispatchLog::getRetryCount, 3)
//                .le(GatewayDispatchLog::getNextRetryTime, LocalDateTime.now());
//        List<GatewayDispatchLog> failedLogs = dispatchLogMapper.selectList(wrapper);
//
//        int successCount = 0;
//        for (GatewayDispatchLog dispatchLog : failedLogs) {
//            try {
//                if (retryDispatch(dispatchLog.getId())) {
//                    successCount++;
//                }
//            } catch (Exception e) {
//                log.error("重发失败: logId={}", dispatchLog.getId(), e);
//            }
//        }
//
//        log.info("批量重发完成: 总数={}, 成功={}", failedLogs.size(), successCount);
//        return successCount;
//    }
//
//    // ==================== 辅助方法 ====================
//
//    /**
//     * 查找指定节点的子节点中指定类型的节点
//     */
//    private TaskChainNode findChildOfType(Long parentId, String deviceType, List<TaskChainNode> allNodes) {
//        return allNodes.stream()
//                .filter(n -> parentId.equals(n.getParentNodeId()) && deviceType.equals(n.getDeviceType()))
//                .findFirst()
//                .orElse(null);
//    }
//
//    /**
//     * 解析节点配置JSON
//     */
//    @SuppressWarnings("unchecked")
//    private Map<String, Object> parseNodeConfig(String nodeConfig) {
//        if (StrUtil.isBlank(nodeConfig)) {
//            return new HashMap<>();
//        }
//        try {
//            return objectMapper.readValue(nodeConfig, Map.class);
//        } catch (Exception e) {
//            return new HashMap<>();
//        }
//    }
//
//    /**
//     * 是否启用自动下发
//     */
//    public boolean isAutoDispatch() {
//        return autoDispatch;
//    }
//}
