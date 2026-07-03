package com.monitorplatform.forward.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitorplatform.common.log.DiagnosticLogReport;
import com.monitorplatform.common.log.DiagnosticLogReporter;
import com.monitorplatform.forward.entity.TaskChainConfig;
import com.monitorplatform.forward.entity.TaskChainNode;
import com.monitorplatform.forward.entity.dto.*;
import com.monitorplatform.forward.entity.vo.DeployBranchDetailVO;
import com.monitorplatform.forward.entity.vo.DeployGatewayStatsVO;
import com.monitorplatform.forward.entity.vo.DeploySummaryVO;
import com.monitorplatform.forward.entity.vo.ChainHealthVO;
import com.monitorplatform.forward.entity.vo.InfoBoardInfoVO;
import com.monitorplatform.forward.entity.vo.StopGatewayProxyResultVO;
import com.monitorplatform.forward.entity.vo.TaskChainNodeVO;
import com.monitorplatform.forward.entity.vo.TaskChainVO;
import com.monitorplatform.forward.entity.vo.TerminalGatewayInfoVO;
import com.monitorplatform.forward.mapper.TaskChainConfigMapper;
import com.monitorplatform.forward.mapper.TaskChainNodeMapper;
import com.monitorplatform.forward.service.GatewayConfigDispatchService;
import com.monitorplatform.forward.service.TaskChainService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 任务链路配置服务实现类
 */
@Slf4j
@Service
public class TaskChainServiceImpl implements TaskChainService {

    @Resource
    private TaskChainConfigMapper chainConfigMapper;

    @Resource
    private TaskChainNodeMapper chainNodeMapper;

    @Autowired(required = false)
    private RestTemplate restTemplate;

    @Autowired(required = false)
    private GatewayConfigDispatchService gatewayConfigDispatchService;

    @Autowired(required = false)
    private com.monitorplatform.forward.service.PublishGatewayConfigService publishGatewayConfigService;

    @Autowired(required = false)
    private DiagnosticLogReporter diagnosticLogReporter;

    /**
     * 是否启用自动下发（链路保存后自动下发到网关）
     */
    @Value("${gateway.dispatch.auto-dispatch:true}")
    private boolean autoDispatch;

    /**
     * 设备服务基础URL（从配置读取）
     */
    @Value("${monitor.device.url:http://localhost:8062}")
    private String deviceServiceUrl;

    // 主节点核心设备类型定义（一对一）
    private static final Set<String> MAIN_NODE_TYPES = new HashSet<>(Arrays.asList(
            "publish_server",           // 发布服务器
            "publish_gateway"           // 发布加密网关
    ));

    // 分支节点核心设备类型定义（一对多）
    private static final Set<String> BRANCH_CORE_TYPES = new HashSet<>(Arrays.asList(
            "terminal_encrypt_gateway", // 终端加密网关
            "info_board"                // 情报板
    ));

    // 可选设备类型定义
    private static final Set<String> OPTIONAL_DEVICE_TYPES = new HashSet<>(Collections.singletonList(
            "content_server"            // 内容识别服务器
    ));


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createChain(CreateChainDTO dto) {
        log.info("创建任务链路（树形结构）: {}", dto.getChainCode());

        // 1. 校验链路编码唯一性
        LambdaQueryWrapper<TaskChainConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskChainConfig::getChainCode, dto.getChainCode())
                .eq(TaskChainConfig::getDeleted, 0);
        Long count = chainConfigMapper.selectCount(wrapper);
        if (count > 0) {
            throw new RuntimeException("链路编码已存在: " + dto.getChainCode());
        }

        // 2. 创建链路配置
        TaskChainConfig config = new TaskChainConfig();
        BeanUtil.copyProperties(dto, config);
        config.setStatus(0);
        config.setDeleted(0);
        config.setVersion(1);
        config.setValidationStatus(0);
        config.setCreateTime(LocalDateTime.now());
        config.setUpdateTime(LocalDateTime.now());
        chainConfigMapper.insert(config);
        Long chainId = config.getId();

        // 3. 递归创建节点树
        int[] totalNodes = {0};
        int[] coreNodes = {0};
        int[] optionalNodes = {0};

        if (CollUtil.isNotEmpty(dto.getRootNodes())) {
            for (ChainNodeDTO rootNode : dto.getRootNodes()) {
                createNodeRecursive(chainId, null, rootNode, totalNodes, coreNodes, optionalNodes);
            }
        }

        // 4. 更新链路统计信息
        config.setTotalNodes(totalNodes[0]);
        config.setCoreNodes(coreNodes[0]);
        config.setOptionalNodes(optionalNodes[0]);
        chainConfigMapper.updateById(config);

        // 5. 校验链路结构（仅在有节点时校验）
        if (totalNodes[0] > 0) {
            validateChainStructure(chainId);
            
            // 6. 自动下发配置到网关
            triggerConfigDispatch(chainId);
        } else {
            log.info("链路暂无节点，跳过结构校验（支持分步创建）");
        }

        log.info("任务链路创建成功，链路ID: {}, 总节点数: {}", chainId, totalNodes[0]);
        return chainId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean createChainNodes(CreateChainNodesDTO dto) {
        log.info("为链路创建节点拓扑结构: chainId={}", dto.getChainId());
        // 1. 校验链路是否存在
        TaskChainConfig config = chainConfigMapper.selectById(dto.getChainId());

        if (config == null || config.getDeleted() == 1) {
            throw new RuntimeException("链路不存在或已删除: chainId=" + dto.getChainId());
        }
        // 删除该链路下所有旧节点（全量替换）
        LambdaQueryWrapper<TaskChainNode> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(TaskChainNode::getChainId, dto.getChainId());
        Long deletedCount = chainNodeMapper.selectCount(deleteWrapper);
        if (deletedCount > 0) {
            chainNodeMapper.delete(deleteWrapper);
        }


        // 3. 递归创建节点树
        int[] totalNodes = {0};  // 总结点数
        int[] coreNodes = {0};   // 核心节点树
        int[] optionalNodes = {0};   // 可选节点数
        // 判断是否有根节点
        if (CollUtil.isNotEmpty(dto.getRootNodes())) {
            for (ChainNodeDTO rootNode : dto.getRootNodes()) {
                createNodeRecursive(dto.getChainId(), null, rootNode, totalNodes, coreNodes, optionalNodes);
            }
        }
        // 4. 更新链路统计信息

        config.setTotalNodes(totalNodes[0]);
        config.setCoreNodes(coreNodes[0]);
        config.setOptionalNodes(optionalNodes[0]);
        config.setUpdateTime(LocalDateTime.now());
        if (dto.getOperatorBy() != null) {
            config.setUpdatedBy(dto.getOperatorBy());
        }
        chainConfigMapper.updateById(config);

        log.info("链路节点创建完成: chainId={}, totalNodes={}, coreNodes={}, optionalNodes={}",
                dto.getChainId(), totalNodes[0], coreNodes[0], optionalNodes[0]);

        // 5. 自动下发配置到发布网关
        if (totalNodes[0] > 0) {
            triggerConfigDispatch(dto.getChainId());
        }

        return true;
    }

    /**
     * 更新任务链路
     * @param dto 更新DTO
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateChain(UpdateChainDTO dto) {
        log.info("更新任务链路: chainId={}", dto.getId());

        // 1. 查询链路是否存在
        TaskChainConfig config = chainConfigMapper.selectById(dto.getId());
        if (config == null || config.getDeleted() == 1) {
            throw new RuntimeException("链路不存在或已删除");
        }

        // 2. 更新链路基本信息
        if (dto.getChainName() != null) {
            config.setChainName(dto.getChainName());
        }
        if (dto.getChainDesc() != null) {
            config.setChainDesc(dto.getChainDesc());
        }
        if (dto.getEnabled() != null) {
            config.setEnabled(dto.getEnabled());
        }
        if (dto.getRemark() != null) {
            config.setRemark(dto.getRemark());
        }
        config.setUpdateTime(LocalDateTime.now());
        chainConfigMapper.updateById(config);

        log.info("链路更新成功: chainId={}", dto.getId());
        return true;
    }

    /**
     * 删除任务链路
     * 1. 先通知发布网关和终端网关停止规则（逻辑删除网关上的 udp_proxy_rule）
     * 2. 物理删除任务链路和其所有节点
     *
     * @param chainId 链路ID
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteChain(Long chainId) {
        log.info("删除任务链路: chainId={}", chainId);

        // 1. 查询链路是否存在
        TaskChainConfig config = chainConfigMapper.selectById(chainId);
        if (config == null || config.getDeleted() == 1) {
            throw new RuntimeException("链路不存在或已删除");
        }

        // 2. 先通知两个网关停止并逻辑删除其规则（在节点删除前调用，确保能拿到IP）
        if (publishGatewayConfigService != null) {
            try {
                publishGatewayConfigService.stopChainRules(chainId);
            } catch (Exception e) {
                // 网关通知失败不阻断删除，仅记录日志
                log.error("通知网关停止规则失败，继续删除链路: chainId={}", chainId, e);
            }
        } else {
            log.warn("发布网关配置服务未注入，跳过网关通知: chainId={}", chainId);
        }

        // 3. 物理删除所有节点
        LambdaQueryWrapper<TaskChainNode> nodeWrapper = new LambdaQueryWrapper<>();
        nodeWrapper.eq(TaskChainNode::getChainId, chainId);
        chainNodeMapper.delete(nodeWrapper);

        // 4. 物理删除链路本身
        chainConfigMapper.deleteById(chainId);

        log.info("链路删除成功（物理删除）: chainId={}", chainId);
        return true;
    }

    /**
     * 查询链路基本信息（不包含节点树）
     * @param chainId 链路ID
     * @return 链路详情
     */
    @Override
    public TaskChainVO getChainById(Long chainId) {
        log.info("查询链路基本信息: chainId={}", chainId);
        
        TaskChainConfig config = chainConfigMapper.selectById(chainId);
        if (config == null || config.getDeleted() == 1) {
            throw new RuntimeException("链路不存在或已删除");
        }
        
        TaskChainVO vo = new TaskChainVO();
        BeanUtil.copyProperties(config, vo);
        return vo;
    }

    /**
     * 递归创建节点
     *
     * @param chainId 链路ID
     * @param parentId 父节点ID（数据库中的实际ID，不是DTO中的parentId）
     * @param nodeDTO 节点DTO
     * @param totalNodes 总节点计数器
     * @param coreNodes 核心节点计数器
     * @param optionalNodes 可选节点计数器
     * @return 当前创建节点的数据库ID
     */
    private Long createNodeRecursive(Long chainId, Long parentId, ChainNodeDTO nodeDTO,
                                     int[] totalNodes, int[] coreNodes, int[] optionalNodes){
        // 1. 校验设备是否存在
        checkDeviceExists(nodeDTO.getDeviceId(), nodeDTO.getDeviceType());

        // 2. 创建当前节点
        // 修改后
        TaskChainNode node = new TaskChainNode();
// 🚩 关键修复：忽略 DTO 中的 id 和 parentId，避免类型转换错误
        BeanUtil.copyProperties(nodeDTO, node, "id", "parentId");

// 手动设置核心字段
        node.setChainId(chainId);
        node.setParentId(parentId); // 这里使用的是方法参数传
        node.setNodeStatus("离线");
        node.setCreateTime(LocalDateTime.now());
        node.setUpdateTime(LocalDateTime.now());
        chainNodeMapper.insert(node);

        Long currentNodeId = node.getId();  // 获取刚插入节点的数据库ID

        log.debug("创建节点成功: id={}, deviceId={}, deviceName={}, parentId={}",
                currentNodeId, nodeDTO.getDeviceId(), nodeDTO.getDeviceName(), parentId);

        // 3. 统计节点数量
        totalNodes[0]++;
        coreNodes[0]++; // 所有节点默认为核心节点

        // 4. 递归创建子节点（将当前节点的数据库ID作为子节点的parentId）
        if (CollUtil.isNotEmpty(nodeDTO.getChildren())) {
            for (ChainNodeDTO child : nodeDTO.getChildren()) {
                createNodeRecursive(chainId, currentNodeId, child, totalNodes, coreNodes, optionalNodes);
            }
        }

        return currentNodeId;
    }

   @Override
   public TaskChainVO getChainTree(Long chainId){
        log.info("查询链路树形结构：chainId={}",chainId);

       // 1. 查询链路配置
       TaskChainConfig config = chainConfigMapper.selectById(chainId);
       if (config == null || config.getDeleted() == 1){
           throw new RuntimeException("链路不存在或已删除");
       }

       // 2. 查询所有节点
       LambdaQueryWrapper<TaskChainNode> wrapper = new LambdaQueryWrapper<>();
       wrapper.eq(TaskChainNode::getChainId, chainId);
       List<TaskChainNode> allNodes = chainNodeMapper.selectList(wrapper);


       // 3. 构建树形VO
       TaskChainVO vo = new TaskChainVO();
       BeanUtil.copyProperties(config, vo);

       // 4. 查找根节点并递归构建子树
       List<TaskChainNode> rootNodes = allNodes.stream()
               .filter(node -> node.getParentId() == null)
               .collect(Collectors.toList());

       List<TaskChainNodeVO> rootNodeVOs = new ArrayList<>();
       for (TaskChainNode rootNode : rootNodes) {
           TaskChainNodeVO nodeVO = buildNodeTree(rootNode, allNodes);
           rootNodeVOs.add(nodeVO);
       }
       vo.setNodes(rootNodeVOs);
       return vo;
   }


    /**
     * 递归构建节点树（核心方法）
     */
    private TaskChainNodeVO buildNodeTree(TaskChainNode node, List<TaskChainNode> allNodes){
        TaskChainNodeVO vo = new TaskChainNodeVO();
        BeanUtil.copyProperties(node,vo);

        // 设置设备类型描述
        vo.setDeviceTypeDesc(getDeviceTypeDesc(node.getDeviceType()));
        // 映射 deviceIp → ipAddress
        vo.setIpAddress(node.getDeviceIp());

        // 查找当前节点的所有子节点
        List<TaskChainNode> children = allNodes.stream()
                .filter(n -> node.getId().equals(n.getParentId()))
                .collect(Collectors.toList());

        // 递归构建子节点
        if (CollUtil.isNotEmpty(children)) {
            List<TaskChainNodeVO> childVOs = new ArrayList<>();
            for (TaskChainNode child : children) {
                TaskChainNodeVO childVO = buildNodeTree(child, allNodes);
                childVOs.add(childVO);
            }
            vo.setChildren(childVOs);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createNode(Long chainId, Long parentId, ChainNodeDTO nodeDTO) {
        log.info("创建节点: chainId={}, parentId={}, deviceId={}",
                chainId, parentId, nodeDTO.getDeviceId());

        // 1. 检查链路是否存在
        TaskChainConfig config = chainConfigMapper.selectById(chainId);
        if (config == null || config.getDeleted() == 1) {
            throw new RuntimeException("链路不存在或已删除");
        }

        // 2. 如果指定了父节点，检查父节点是否存在
        if (parentId != null) {
            TaskChainNode parentNode = chainNodeMapper.selectById(parentId);
            if (parentNode == null || !parentNode.getChainId().equals(chainId)) {
                throw new RuntimeException("父节点不存在或不属于当前链路");
            }
        }

        // 3. 校验设备是否存在
        checkDeviceExists(nodeDTO.getDeviceId(), nodeDTO.getDeviceType());

        LambdaQueryWrapper<TaskChainNode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskChainNode::getChainId, chainId)
                .eq(TaskChainNode::getDeviceId, nodeDTO.getDeviceId());
        Long count = chainNodeMapper.selectCount(wrapper);
        if (count > 0) {
            throw new RuntimeException("设备已在当前链路中使用: " + nodeDTO.getDeviceId());
        }

        // 5. 创建节点
        TaskChainNode node = new TaskChainNode();
        BeanUtil.copyProperties(nodeDTO, node);
        node.setChainId(chainId);
        node.setParentId(parentId);
        node.setNodeStatus("离线");
        node.setCreateTime(LocalDateTime.now());
        node.setUpdateTime(LocalDateTime.now());
        chainNodeMapper.insert(node);

        // 6. 更新链路统计
        config.setTotalNodes(config.getTotalNodes() + 1);
        config.setCoreNodes(config.getCoreNodes() + 1); // 默认核心节点
        config.setUpdateTime(LocalDateTime.now());
        chainConfigMapper.updateById(config);

        // 7. 注意：不在此处校验链路结构，因为分步创建时链路可能还未完成
        // 用户应该在所有节点添加完毕后，主动调用 /chain/validate/structure/{chainId} 接口进行校验

        log.info("节点创建成功: nodeId={}", node.getId());
        return node.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteNode(Long nodeId) {
        log.info("删除节点（级联）: nodeId={}", nodeId);

        // 1. 查询节点
        TaskChainNode node = chainNodeMapper.selectById(nodeId);
        if (node == null) {
            throw new RuntimeException("节点不存在");
        }

        // 2. 递归收集所有需要删除的节点ID（包括子孙节点）
        List<Long> nodeIdsToDelete = new ArrayList<>();
        collectDescendants(nodeId, nodeIdsToDelete);

        // 3. 统计删除的节点数量
        int totalDeleted = nodeIdsToDelete.size();

        // 4. 批量删除节点
        chainNodeMapper.deleteBatchIds(nodeIdsToDelete);

        // 5. 更新链路统计
        TaskChainConfig config = chainConfigMapper.selectById(node.getChainId());
        config.setTotalNodes(config.getTotalNodes() - totalDeleted);
        config.setCoreNodes(config.getCoreNodes() - totalDeleted); // 所有节点都是核心节点
        config.setUpdateTime(LocalDateTime.now());
        chainConfigMapper.updateById(config);

        // 6. 注意：不在此处校验链路结构，删除后链路可能不完整
        // 用户可以在需要时主动调用校验接口

        log.info("节点删除成功: 删除了{}个节点（包括子节点）", totalDeleted);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean moveNode(Long nodeId, Long newParentId) {
        log.info("移动节点: nodeId={}, newParentId={}", nodeId, newParentId);

        // 1. 查询节点
        TaskChainNode node = chainNodeMapper.selectById(nodeId);
        if (node == null) {
            throw new RuntimeException("节点不存在");
        }

        // 2. 检查新父节点是否存在
        if (newParentId != null) {
            TaskChainNode newParent = chainNodeMapper.selectById(newParentId);
            if (newParent == null || !newParent.getChainId().equals(node.getChainId())) {
                throw new RuntimeException("新父节点不存在或不属于同一链路");
            }

            // 3. 检查是否形成循环（新父节点不能是当前节点的后代）
            if (isDescendant(newParentId, nodeId)) {
                throw new RuntimeException("不能将节点移动到其后代节点下，会形成循环");
            }
        }

        // 4. 更新父节点
        node.setParentId(newParentId);
        node.setUpdateTime(LocalDateTime.now());
        chainNodeMapper.updateById(node);

        // 5. 注意：不在此处校验链路结构，移动后链路可能不完整
        // 用户可以在需要时主动调用校验接口

        log.info("节点移动成功");
        return true;
    }

    /**
     * 检查节点A是否是节点B的后代
     */
    private boolean isDescendant(Long nodeA, Long nodeB) {
        TaskChainNode current = chainNodeMapper.selectById(nodeA);
        while (current != null && current.getParentId() != null) {
            if (current.getParentId().equals(nodeB)) {
                return true;
            }
            current = chainNodeMapper.selectById(current.getParentId());
        }
        return false;
    }

    @Override
    public Boolean validateChainStructure(Long chainId) {
        log.info("校验链路结构: chainId={}", chainId);

        // 1. 查询所有节点
        LambdaQueryWrapper<TaskChainNode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskChainNode::getChainId, chainId);
        List<TaskChainNode> allNodes = chainNodeMapper.selectList(wrapper);

        if (allNodes.isEmpty()) {
            throw new RuntimeException("链路没有任何节点");
        }

        // 2. 校验是否包含所有四种必备设备
        Set<String> presentTypes = allNodes.stream()
                .map(TaskChainNode::getDeviceType)
                .collect(Collectors.toSet());

        String errorMsg = "任务链路必须包括四种：发布服务器、发布端网关、终端加密网关、情报板，且顺序必须一致";

        if (!presentTypes.contains("publish_server") ||
                !presentTypes.contains("publish_gateway") ||
                !presentTypes.contains("terminal_encrypt_gateway") ||
                !presentTypes.contains("info_board")) {
            throw new RuntimeException(errorMsg);
        }

        // 3. 检查根节点（必须有发布服务器）
        List<TaskChainNode> rootNodes = allNodes.stream()
                .filter(node -> node.getParentId() == null)
                .collect(Collectors.toList());

        if (rootNodes.isEmpty()) {
            throw new RuntimeException("链路必须有根节点");
        }

        for (TaskChainNode root : rootNodes) {
            if (!"publish_server".equals(root.getDeviceType())) {
                throw new RuntimeException("链路起点必须是发布服务器: " + root.getDeviceName());
            }
            // 递归校验子节点顺序
            validateNodeHierarchy(root, allNodes, 1);
        }

        log.info("链路结构校验通过");
        return true;
    }

    /**
     * 递归校验节点层级顺序
     * level: 1-发布服务器, 2-发布端网关, 3-终端加密网关, 4-情报板
     */
    private void validateNodeHierarchy(TaskChainNode parent, List<TaskChainNode> allNodes, int level) {
        List<TaskChainNode> children = allNodes.stream()
                .filter(node -> parent.getId().equals(node.getParentId()))
                .collect(Collectors.toList());

        // 如果不是最后一层（情报板），必须有子节点
        if (level < 4 && children.isEmpty()) {
            throw new RuntimeException("任务链路不完整，[" + parent.getDeviceName() + "] 后面缺少后续设备");
        }

        for (TaskChainNode child : children) {
            String childType = child.getDeviceType();

            switch (level) {
                case 1: // 当前是发布服务器 -> 下一级必须是发布端网关
                    if (!"publish_gateway".equals(childType)) {
                        throw new RuntimeException("顺序错误：发布服务器后面只能连接发布端网关");
                    }
                    validateNodeHierarchy(child, allNodes, 2);
                    break;
                case 2: // 当前是发布加密网关 -> 下一级必须是终端加密网关
                    if (!"terminal_encrypt_gateway".equals(childType)) {
                        throw new RuntimeException("顺序错误：发布端网关后面只能连接终端加密网关");
                    }
                    validateNodeHierarchy(child, allNodes, 3);
                    break;
                case 3: // 当前是终端加密网关 -> 下一级必须是情报板
                    if (!"info_board".equals(childType)) {
                        throw new RuntimeException("顺序错误：终端加密网关后面只能连接情报板");
                    }
                    validateNodeHierarchy(child, allNodes, 4);
                    break;
                case 4: // 当前是情报板 -> 不能再有子节点
                    throw new RuntimeException("结构错误：情报板已经是终端节点，后面不能再连接其他设备");
            }
        }
    }

    /**
     * 检查节点的后代中是否有指定类型的设备
     */
    private boolean hasDescendantOfType(Long nodeId, String deviceType, List<TaskChainNode> allNodes) {
        List<TaskChainNode> children = allNodes.stream()
                .filter(node -> nodeId.equals(node.getParentId()))
                .collect(Collectors.toList());

        for (TaskChainNode child : children) {
            if (deviceType.equals(child.getDeviceType())) {
                return true;
            }
            if (hasDescendantOfType(child.getId(), deviceType, allNodes)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 更新节点
     * @param nodeId 节点ID
     * @param nodeDTO 节点数据
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateNode(Long nodeId, ChainNodeDTO nodeDTO) {
        log.info("更新节点: nodeId={}", nodeId);

        // 1. 查询节点是否存在
        TaskChainNode oldNode = chainNodeMapper.selectById(nodeId);
        if (oldNode == null) {
            throw new RuntimeException("节点不存在");
        }

        TaskChainConfig config = chainConfigMapper.selectById(oldNode.getChainId());
        if (config == null) {
            throw new RuntimeException("链路不存在");
        }

        // 2. 如果修改了设备ID，需要校验
        if (nodeDTO.getDeviceId() != null && !nodeDTO.getDeviceId().equals(oldNode.getDeviceId())) {
            // 校验设备存在性
            checkDeviceExists(nodeDTO.getDeviceId(), nodeDTO.getDeviceType());

            // 检查新设备是否已在该链路中使用
            LambdaQueryWrapper<TaskChainNode> checkWrapper = new LambdaQueryWrapper<>();
            checkWrapper.eq(TaskChainNode::getChainId, oldNode.getChainId())
                    .eq(TaskChainNode::getDeviceId, nodeDTO.getDeviceId())
                    .ne(TaskChainNode::getId, nodeId);
            if (chainNodeMapper.selectCount(checkWrapper) > 0) {
                throw new RuntimeException("设备已在该链路中使用");
            }
        }

        // 3. 更新节点
        if (nodeDTO.getDeviceId() != null) {
            oldNode.setDeviceId(nodeDTO.getDeviceId());
        }
        if (nodeDTO.getDeviceName() != null) {
            oldNode.setDeviceName(nodeDTO.getDeviceName());
        }
        if (nodeDTO.getDeviceType() != null) {
            oldNode.setDeviceType(nodeDTO.getDeviceType());
        }
        if (nodeDTO.getRemark() != null) {
            oldNode.setRemark(nodeDTO.getRemark());
        }
        oldNode.setUpdateTime(LocalDateTime.now());
        chainNodeMapper.updateById(oldNode);

        // 5. 注意：不在此处校验链路结构，更新后链路可能不完整
        // 用户可以在需要时主动调用校验接口

        log.info("节点更新成功: nodeId={}", nodeId);
        return true;
    }

    /**
     * 查询节点详情
     * @param nodeId 节点ID
     * @return 节点详情
     */
    @Override
    public TaskChainNodeVO getNodeById(Long nodeId) {
        TaskChainNode node = chainNodeMapper.selectById(nodeId);
        if (node == null) {
            return null;
        }
        return convertNodeToVO(node);
    }

    /**
     * 转换节点实体为VO
     */
    private TaskChainNodeVO convertNodeToVO(TaskChainNode node) {
        TaskChainNodeVO vo = new TaskChainNodeVO();
        BeanUtil.copyProperties(node, vo);
        vo.setDeviceTypeDesc(getDeviceTypeDesc(node.getDeviceType()));
        // 映射 deviceIp → ipAddress
        vo.setIpAddress(node.getDeviceIp());
        return vo;
    }

    /**
     * 递归收集节点及其所有后代节点的ID
     */
    private void collectDescendants(Long nodeId, List<Long> result) {
        result.add(nodeId);

        // 查找子节点
        LambdaQueryWrapper<TaskChainNode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskChainNode::getParentId, nodeId);
        List<TaskChainNode> children = chainNodeMapper.selectList(wrapper);

        // 递归收集子节点的后代
        for (TaskChainNode child : children) {
            collectDescendants(child.getId(), result);
        }
    }

    /**
     * 获取设备类型描述
     */
    private String getDeviceTypeDesc(String deviceType) {
        switch (deviceType) {
            case "publish_server": return "信息发布服务器";
            case "publish_gateway": return "发布端加密网关";
            case "terminal_encrypt_gateway": return "终端加密网关";
            case "content_server": return "内容识别服务器";
            case "info_board": return "情报板";
            case "camera": return "摄像设备";
            default: return deviceType;
        }
    }

    /**
     * 校验设备是否存在于unified_device_info表中
     */
    private void checkDeviceExists(String deviceId, String deviceType) {
        if (restTemplate == null) {
            log.warn("RestTemplate未配置，跳过设备存在性校验");
            return;
        }

        try {
            String url = deviceServiceUrl + "/device/unified/" + deviceType + "/" + deviceId + "/detail";
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response == null || !Integer.valueOf(200).equals(response.get("code"))) {
                throw new RuntimeException("设备不存在或未在平台配置: " + deviceId);
            }

            Map<String, Object> deviceData = (Map<String, Object>) response.get("data");
            if (deviceData == null || deviceData.get("deviceId") == null) {
                throw new RuntimeException("设备不存在或未在平台配置: " + deviceId);
            }

        } catch (Exception e) {
            log.error("校验设备存在性失败: deviceId={}, deviceType={}", deviceId, deviceType, e);
            throw new RuntimeException("设备不存在或未在平台配置: " + deviceId + ". 请先在设备管理中添加此设备");
        }
    }

    /**
     * 校验设备是否已在其他链路中使用（全局唯一性）
     * @param deviceId 设备ID
     * @param excludeChainId 排除的链路ID（当前正在操作的链路），null表示不排除
     */
    private void checkDeviceGlobalUnique(String deviceId, Long excludeChainId) {
        LambdaQueryWrapper<TaskChainNode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskChainNode::getDeviceId, deviceId);
        if (excludeChainId != null) {
            wrapper.ne(TaskChainNode::getChainId, excludeChainId);
        }
        List<TaskChainNode> conflicts = chainNodeMapper.selectList(wrapper);
        if (!conflicts.isEmpty()) {
            TaskChainNode conflict = conflicts.get(0);
            // 查询冲突链路信息
            String chainDesc;
            TaskChainConfig conflictChain = chainConfigMapper.selectById(conflict.getChainId());
            if (conflictChain != null) {
                chainDesc = String.format("%s（编码: %s, ID: %d）",
                        conflictChain.getChainName(), conflictChain.getChainCode(), conflict.getChainId());
            } else {
                chainDesc = String.format("ID: %d（该链路可能已被删除）", conflict.getChainId());
            }
            throw new RuntimeException(String.format(
                    "设备 [%s(%s)] 已被链路 [%s] 占用，同一设备不能同时分配到多条链路，请先从原链路中移除该设备",
                    conflict.getDeviceName() != null ? conflict.getDeviceName() : deviceId,
                    deviceId, chainDesc));
        }
    }

    // ==================== 链路查询和管理 ====================

    /**
     * 分页查询链路列表
     */
    @Override
    public Page<TaskChainVO> queryChainPage(ChainQueryDTO queryDTO) {
        log.info("分页查询链路列表: {}", queryDTO);

        Page<TaskChainConfig> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        LambdaQueryWrapper<TaskChainConfig> wrapper = new LambdaQueryWrapper<>();
        
        // 条件查询
        if (StrUtil.isNotBlank(queryDTO.getChainCode())) {
            wrapper.like(TaskChainConfig::getChainCode, queryDTO.getChainCode());
        }
        if (StrUtil.isNotBlank(queryDTO.getChainName())) {
            wrapper.like(TaskChainConfig::getChainName, queryDTO.getChainName());
        }
        if (queryDTO.getEnabled() != null) {
            wrapper.eq(TaskChainConfig::getEnabled, queryDTO.getEnabled());
        }
        wrapper.eq(TaskChainConfig::getDeleted, 0)
                .orderByDesc(TaskChainConfig::getCreateTime);

        Page<TaskChainConfig> configPage = chainConfigMapper.selectPage(page, wrapper);
        
        // 转换为VO
        Page<TaskChainVO> voPage = new Page<>(configPage.getCurrent(), configPage.getSize(), configPage.getTotal());
        List<TaskChainVO> voList = configPage.getRecords().stream().map(config -> {
            TaskChainVO vo = new TaskChainVO();
            BeanUtil.copyProperties(config, vo);
            return vo;
        }).collect(Collectors.toList());
        voPage.setRecords(voList);
        
        return voPage;
    }

    /**
     * 校验链路合法性（与 validateChainStructure 相同）
     */
    @Override
    public Boolean validateChain(Long chainId) {
        return validateChainStructure(chainId);
    }

    /**
     * 同步链路状态（根据节点状态更新链路状态）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean syncChainStatus(Long chainId) {
        log.info("同步链路状态: chainId={}", chainId);

        TaskChainConfig config = chainConfigMapper.selectById(chainId);
        if (config == null || config.getDeleted() == 1) {
            throw new RuntimeException("链路不存在或已删除");
        }

        // 查询所有节点
        LambdaQueryWrapper<TaskChainNode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskChainNode::getChainId, chainId);
        List<TaskChainNode> nodes = chainNodeMapper.selectList(wrapper);

        if (nodes.isEmpty()) {
            config.setStatus(0); // 无节点，设为离线
            chainConfigMapper.updateById(config);
            return true;
        }

        // 统计节点状态
        long onlineCount = nodes.stream().filter(n -> "在线".equals(n.getNodeStatus())).count();
        long offlineCount = nodes.stream().filter(n -> "离线".equals(n.getNodeStatus())).count();
        long errorCount = nodes.stream().filter(n -> "异常".equals(n.getNodeStatus())).count();

        // 更新链路状态
        if (errorCount > 0) {
            config.setStatus(2); // 异常
        } else if (offlineCount == nodes.size()) {
            config.setStatus(0); // 全部离线
        } else if (onlineCount == nodes.size()) {
            config.setStatus(1); // 全部在线
        } else {
            config.setStatus(1); // 部分在线
        }
        
        config.setUpdateTime(LocalDateTime.now());
        chainConfigMapper.updateById(config);
        
        log.info("链路状态同步完成: chainId={}, status={}", chainId, config.getStatus());
        return true;
    }

    /**
     * 批量同步所有链路状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer syncAllChainStatus() {
        log.info("批量同步所有链路状态");

        LambdaQueryWrapper<TaskChainConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskChainConfig::getDeleted, 0);
        List<TaskChainConfig> allChains = chainConfigMapper.selectList(wrapper);

        int syncCount = 0;
        for (TaskChainConfig chain : allChains) {
            try {
                syncChainStatus(chain.getId());
                syncCount++;
            } catch (Exception e) {
                log.error("同步链路状态失败: chainId={}", chain.getId(), e);
            }
        }

        log.info("批量同步完成，总数: {}, 成功: {}", allChains.size(), syncCount);
        return syncCount;
    }

    /**
     * 查询设备被占用情况
     */
    @Override
    public List<TaskChainVO> queryChainsByDevice(String deviceId) {
        log.info("查询设备占用情况: deviceId={}", deviceId);

        // 1. 查询使用了该设备的节点
        LambdaQueryWrapper<TaskChainNode> nodeWrapper = new LambdaQueryWrapper<>();
        nodeWrapper.eq(TaskChainNode::getDeviceId, deviceId);
        List<TaskChainNode> nodes = chainNodeMapper.selectList(nodeWrapper);

        if (nodes.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 获取链路ID列表
        List<Long> chainIds = nodes.stream()
                .map(TaskChainNode::getChainId)
                .distinct()
                .collect(Collectors.toList());

        // 3. 查询链路详情
        List<TaskChainVO> result = new ArrayList<>();
        for (Long chainId : chainIds) {
            TaskChainConfig config = chainConfigMapper.selectById(chainId);
            if (config != null && config.getDeleted() == 0) {
                TaskChainVO vo = new TaskChainVO();
                BeanUtil.copyProperties(config, vo);
                result.add(vo);
            }
        }

        return result;
    }

    /**
     * 检查设备是否可用（未被占用且状态正常）
     */
    @Override
    public Boolean checkDeviceAvailable(String deviceId) {
        log.info("检查设备可用性: deviceId={}", deviceId);

        // 1. 检查设备是否已被使用
        LambdaQueryWrapper<TaskChainNode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskChainNode::getDeviceId, deviceId);
        Long count = chainNodeMapper.selectCount(wrapper);

        if (count > 0) {
            log.info("设备已被占用: deviceId={}, 占用数: {}", deviceId, count);
            return false;
        }

        // 2. TODO: 可以进一步调用设备服务检查设备状态
        
        return true;
    }

    // ==================== 配置下发相关 ====================

    /**
     * 手动下发链路配置到网关
     */
    @Override
    public DeploySummaryVO manualDeployConfig(Long chainId) {
        log.info("【手动下发】开始下发链路配置: chainId={}", chainId);
        
        // 1. 校验链路是否存在
        TaskChainConfig config = chainConfigMapper.selectById(chainId);
        if (config == null || config.getDeleted() == 1) {
            throw new RuntimeException("链路不存在或已删除: chainId=" + chainId);
        }
        
        // 2. 校验链路是否有节点
        LambdaQueryWrapper<TaskChainNode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskChainNode::getChainId, chainId);
        Long nodeCount = chainNodeMapper.selectCount(wrapper);
        if (nodeCount == 0) {
            throw new RuntimeException("链路暂无节点，无法下发配置: chainId=" + chainId);
        }
        
        // 3. 调用下发服务（指定为手动下发）
        if (publishGatewayConfigService == null) {
            throw new RuntimeException("发布网关配置服务未初始化");
        }
        
        Map<String, Object> result = publishGatewayConfigService.deployChainToPublishGateway(chainId, "manual");
        
        log.info("【手动下发】链路配置下发完成: chainId={}", chainId);
        return convertDeploySummary(result);
    }

    /**
     * 触发配置下发到网关
     * @param chainId 链路ID
     */
    private void triggerConfigDispatch(Long chainId) {
        if (!autoDispatch) {
            log.info("自动下发已禁用，跳过配置下发: chainId={}", chainId);
            return;
        }

        if (publishGatewayConfigService == null) {
            log.warn("发布网关配置服务未注入，跳过下发: chainId={}", chainId);
            return;
        }

        try {
            log.info("==========================================");
            log.info("  【自动下发】触发配置下发到发布网关");
            log.info("  链路ID: {}", chainId);
            log.info("==========================================");
            
            Map<String, Object> result = publishGatewayConfigService.deployChainToPublishGateway(chainId);
            
            Integer successCount = (Integer) result.get("successCount");
            Integer failedCount = (Integer) result.get("failedCount");
            
            log.info("【自动下发】配置下发完成: 成功={}, 失败={}", successCount, failedCount);
            
        } catch (Exception e) {
            // 下发失败不影响链路保存，仅记录日志
            log.error("【自动下发】配置下发失败，稍后可手动重试: chainId={}", chainId, e);
        }
    }
    
    /**
     * 停止终端网关代理规则
     */
    @Override
    public StopGatewayProxyResultVO stopTerminalGatewayProxy(Long chainId, StopGatewayProxyRequestDTO params) {
        StopGatewayProxyResultVO result = new StopGatewayProxyResultVO();
        
        try {
            // 1. 查询链路树，获取终端网关信息
            TaskChainVO chainVO = getChainTree(chainId);
            if (chainVO == null) {
                result.put("success", false);
                result.put("message", "链路不存在: chainId=" + chainId);
                return result;
            }
            
            // 2. 从节点树中查找终端网关
            Map<String, Object> terminalGateway = findTerminalGatewayInNodes(chainVO.getNodes());
            if (terminalGateway == null) {
                result.put("success", false);
                result.put("message", "未找到终端网关节点");
                return result;
            }
            
            // 3. 提取终端网关的IP和端口
            String gatewayIp = (String) terminalGateway.get("ipAddress");
            Object portObj = terminalGateway.get("port");
            Integer gatewayPort = portObj != null ? Integer.parseInt(String.valueOf(portObj)) : 8093;
            
            if (StrUtil.isBlank(gatewayIp)) {
                result.put("success", false);
                result.put("message", "终端网关IP地址为空");
                return result;
            }
            
            // 4. 构造ruleId（格式: chainId_terminal）
            String ruleId = chainId + "_terminal";
            
            // 5. 调用终端网关的停止接口
            String stopUrl = String.format("http://%s:%d/udp-proxy/stop/%s", gatewayIp, gatewayPort, ruleId);
            
            log.info("调用终端网关停止代理: url={}", stopUrl);
            
            if (restTemplate == null) {
                result.put("success", false);
                result.put("message", "RestTemplate未配置");
                return result;
            }
            
            Map<String, Object> requestBody = new HashMap<>();
            if (params != null) {
                requestBody.put("reason", params.getReason());
                requestBody.put("operator", params.getOperator());
            }
            
            org.springframework.http.ResponseEntity<Map> response = 
                restTemplate.postForEntity(stopUrl, requestBody, Map.class);
            
            // 6. 处理响应
            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> responseBody = response.getBody();
                
                result.put("success", true);
                result.put("chainId", chainId);
                result.put("ruleId", ruleId);
                result.put("gatewayIp", gatewayIp);
                result.put("gatewayPort", gatewayPort);
                result.put("message", "成功停止终端网关代理");
                result.put("gatewayResponse", responseBody);
                
                log.info("✅ 停止网关代理成功: ruleId={}", ruleId);
            } else {
                result.put("success", false);
                result.put("message", "终端网关返回错误: " + response.getStatusCode());
                log.warn("❌ 停止网关代理失败: {}", response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("停止终端网关代理异常: chainId={}", chainId, e);
            result.put("success", false);
            result.put("message", "调用终端网关异常: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 通过情报板 IP 查询对应的终端网关 IP 和端口
     *
     * 两步查询：
     *   1. 找 device_type='info_board' AND device_ip=boardIp 的情报板节点
     *   2. 通过 parent_node_id 查父节点（terminal_encrypt_gateway）
     */
    @Override
    public TerminalGatewayInfoVO getTerminalGatewayByBoardIp(String boardIp) {
        TerminalGatewayInfoVO result = new TerminalGatewayInfoVO();

        if (boardIp == null || boardIp.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "boardIp 不能为空");
            return result;
        }

        log.info("根据情报板 IP 查询终端网关: boardIp={}", boardIp);

        // Step 1: 找情报板节点（同IP可能存在于多条链路，取chainId最大的作为最新链路）
        LambdaQueryWrapper<TaskChainNode> boardQuery = new LambdaQueryWrapper<>();
        boardQuery.eq(TaskChainNode::getDeviceType, "info_board")
                  .eq(TaskChainNode::getDeviceIp, boardIp)
                  .orderByDesc(TaskChainNode::getChainId);
        List<TaskChainNode> boardNodes = chainNodeMapper.selectList(boardQuery);

        if (boardNodes.isEmpty()) {
            result.put("success", false);
            result.put("message", "未找到情报板节点，IP=" + boardIp);
            log.warn("未找到情报板节点: boardIp={}", boardIp);
            return result;
        }

        // 取第一个匹配（情报板 IP 全局唯一）
        TaskChainNode boardNode = boardNodes.get(0);
        Long parentNodeId = boardNode.getParentId();

        if (parentNodeId == null) {
            result.put("success", false);
            result.put("message", "情报板节点没有父节点（链路结构异常）");
            return result;
        }

        // Step 2: 查父节点（终端网关）
        TaskChainNode gatewayNode = chainNodeMapper.selectById(parentNodeId);

        if (gatewayNode == null) {
            result.put("success", false);
            result.put("message", "未找到终端网关节点: parentNodeId=" + parentNodeId);
            return result;
        }

        if (!"terminal_encrypt_gateway".equals(gatewayNode.getDeviceType())) {
            result.put("success", false);
            result.put("message", "情报板的父节点不是终端网关，实际类型=" + gatewayNode.getDeviceType());
            return result;
        }

        String gatewayIp = gatewayNode.getDeviceIp();
        if (gatewayIp == null || gatewayIp.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "终端网关节点 IP 地址为空，deviceId=" + gatewayNode.getDeviceId());
            return result;
        }

        log.info("查询成功: 情报板 IP={} -> 终端网关 IP={}, 链路 ID={}",
                boardIp, gatewayIp, boardNode.getChainId());

        result.put("success", true);
        result.put("boardIp", boardIp);
        result.put("boardNodeId", boardNode.getId());
        result.put("chainId", boardNode.getChainId());
        result.put("terminalGatewayIp", gatewayIp);
        result.put("terminalGatewayNodeId", gatewayNode.getId());
        result.put("terminalGatewayDeviceId", gatewayNode.getDeviceId());
        result.put("terminalGatewayDeviceName", gatewayNode.getDeviceName());
        return result;
    }

    /**
     * 通过链路 ID 查询情报板 Ip
     */
    @Override
    public InfoBoardInfoVO getInfoBoardByChainId(Long chainId) {
        InfoBoardInfoVO result = new InfoBoardInfoVO();

        if (chainId == null) {
            result.put("success", false);
            result.put("message", "chainId 不能为空");
            return result;
        }

        log.info("根据链路 ID 查询情报板IP: chainId={}", chainId);

        // 查询情报板节点
        LambdaQueryWrapper<TaskChainNode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskChainNode::getChainId, chainId)
               .eq(TaskChainNode::getDeviceType, "info_board");
        List<TaskChainNode> boardNodes = chainNodeMapper.selectList(wrapper);

        if (boardNodes.isEmpty()) {
            result.put("success", false);
            result.put("message", "未找到情报板节点，chainId=" + chainId);
            log.warn("未找到情报板节点: chainId={}", chainId);
            return result;
        }

        // 取第一个情报板节点
        TaskChainNode boardNode = boardNodes.get(0);
        String infoBoardIp = boardNode.getDeviceIp();

        if (infoBoardIp == null || infoBoardIp.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "情报板节点 IP 地址为空，deviceId=" + boardNode.getDeviceId());
            return result;
        }

        // 情报板默认端口 9520
        Integer infoBoardPort = 9520;

        log.info("查询成功: chainId={} -> 情报板 IP={}, 端口={}", chainId, infoBoardIp, infoBoardPort);

        result.put("success", true);
        result.put("chainId", chainId);
        result.put("infoBoardIp", infoBoardIp);
        result.put("infoBoardPort", infoBoardPort);
        result.put("infoBoardNodeId", boardNode.getId());
        result.put("infoBoardDeviceId", boardNode.getDeviceId());
        result.put("infoBoardDeviceName", boardNode.getDeviceName());
        return result;
    }

    /**
     * 递归查找终端网关节点（从链路树形结构中搜索）
     */
    private Map<String, Object> findTerminalGatewayInNodes(List<TaskChainNodeVO> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }
        
        for (TaskChainNodeVO node : nodes) {
            // 检查当前节点是否是终端网关
            if ("terminal_encrypt_gateway".equals(node.getDeviceType())) {
                Map<String, Object> gatewayInfo = new HashMap<>();
                gatewayInfo.put("nodeId", node.getId());
                gatewayInfo.put("deviceId", node.getDeviceId());
                gatewayInfo.put("deviceName", node.getDeviceName());
                gatewayInfo.put("ipAddress", node.getIpAddress());
                gatewayInfo.put("port", node.getPort());
                return gatewayInfo;
            }
            
            // 递归查找子节点
            if (CollUtil.isNotEmpty(node.getChildren())) {
                Map<String, Object> found = findTerminalGatewayInNodes(node.getChildren());
                if (found != null) {
                    return found;
                }
            }
        }
        
        return null;
    }

    private DeploySummaryVO convertDeploySummary(Map<String, Object> result) {
        DeploySummaryVO vo = new DeploySummaryVO();
        if (result == null) {
            return vo;
        }
        Object chainIdObj = result.get("chainId");
        if (chainIdObj != null) {
            vo.setChainId(Long.valueOf(String.valueOf(chainIdObj)));
        }
        Object totalBranchesObj = result.get("totalBranches");
        if (totalBranchesObj != null) {
            vo.setTotalBranches(Integer.valueOf(String.valueOf(totalBranchesObj)));
        }
        vo.setPublishGateway(convertGatewayStats((Map<String, Object>) result.get("publishGateway")));
        vo.setTerminalGateway(convertGatewayStats((Map<String, Object>) result.get("terminalGateway")));
        vo.setPublishClient(convertGatewayStats((Map<String, Object>) result.get("publishClient")));

        Object detailsObj = result.get("details");
        if (detailsObj instanceof List) {
            List<DeployBranchDetailVO> details = new ArrayList<>();
            for (Object item : (List<?>) detailsObj) {
                if (!(item instanceof Map)) {
                    continue;
                }
                Map<String, Object> map = (Map<String, Object>) item;
                DeployBranchDetailVO detailVO = new DeployBranchDetailVO();
                detailVO.setBranchCode(map.get("branchCode") == null ? null : String.valueOf(map.get("branchCode")));
                detailVO.setPublishGatewaySuccess(toBoolean(map.get("publishGatewaySuccess")));
                detailVO.setTerminalGatewaySuccess(toBoolean(map.get("terminalGatewaySuccess")));
                detailVO.setPublishClientSuccess(toBoolean(map.get("publishClientSuccess")));
                detailVO.setTerminalGatewayIp(map.get("terminalGatewayIp") == null ? null : String.valueOf(map.get("terminalGatewayIp")));
                details.add(detailVO);
            }
            vo.setDetails(details);
        }
        return vo;
    }

    private DeployGatewayStatsVO convertGatewayStats(Map<String, Object> source) {
        if (source == null) {
            return null;
        }
        DeployGatewayStatsVO vo = new DeployGatewayStatsVO();
        if (source.get("successCount") != null) {
            vo.setSuccessCount(Integer.valueOf(String.valueOf(source.get("successCount"))));
        }
        if (source.get("failedCount") != null) {
            vo.setFailedCount(Integer.valueOf(String.valueOf(source.get("failedCount"))));
        }
        return vo;
    }

    private Boolean toBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.valueOf(String.valueOf(value));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean setChainEnabled(Long chainId, Integer enabled) {
        log.info("启用/停用链路: chainId={}, enabled={}", chainId, enabled);

        TaskChainConfig config = chainConfigMapper.selectById(chainId);
        if (config == null || config.getDeleted() == 1) {
            throw new RuntimeException("链路不存在或已删除");
        }

        // 1. 更新管控平台数据库
        config.setEnabled(enabled);
        config.setStatus(enabled == 1 ? 1 : 0);
        config.setUpdateTime(LocalDateTime.now());
        chainConfigMapper.updateById(config);
        log.info("链路状态已更新: chainId={}, enabled={}, status={}", chainId, enabled, config.getStatus());

        // 2. 通知两个网关切换规则状态
        if (publishGatewayConfigService != null) {
            try {
                publishGatewayConfigService.setChainEnabled(chainId, enabled);
            } catch (Exception e) {
                log.error("通知网关切换状态失败，不阻断本地修改: chainId={}", chainId, e);
            }
        } else {
            log.warn("发布网关配置服务未注入，跳过网关通知: chainId={}", chainId);
        }

        log.info("链路 {} {} 成功", chainId, enabled == 1 ? "已启用" : "已停用");
        return true;
    }

    // ==================== 链路健康度监控 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChainHealthVO checkChainHealth(Long chainId) {
        long startTime = System.currentTimeMillis();
        log.info("开始链路健康度检查: chainId={}", chainId);

        // 1. 校验链路是否存在
        TaskChainConfig config = chainConfigMapper.selectById(chainId);
        if (config == null || config.getDeleted() == 1) {
            throw new RuntimeException("链路不存在或已删除: chainId=" + chainId);
        }

        // 2. 查询所有节点
        LambdaQueryWrapper<TaskChainNode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskChainNode::getChainId, chainId);
        List<TaskChainNode> nodes = chainNodeMapper.selectList(wrapper);

        // 3. 初始化统计数据
        int totalNodes = nodes.size();
        int onlineNodes = 0;
        int offlineNodes = 0;
        int errorNodes = 0;
        int coreOnlineNodes = 0;
        int coreTotalNodes = 0;
        List<ChainHealthVO.UnhealthyNodeInfo> unhealthyList = new ArrayList<>();

        // 4. 逐节点探测设备状态
        for (TaskChainNode node : nodes) {
            Map<String, Object> probeResult = probeDeviceStatus(node);
            String actualStatus = (String) probeResult.get("status");
            @SuppressWarnings("unchecked")
            Map<String, Object> deviceData = (Map<String, Object>) probeResult.get("deviceData");
            boolean isCore = isCoreNodeType(node.getDeviceType());

            if (isCore) {
                coreTotalNodes++;
            }

            if ("在线".equals(actualStatus)) {
                onlineNodes++;
                if (isCore) {
                    coreOnlineNodes++;
                }
                // 更新节点状态
                if (!"在线".equals(node.getNodeStatus())) {
                    node.setNodeStatus("在线");
                    node.setUpdateTime(LocalDateTime.now());
                    chainNodeMapper.updateById(node);
                }
            } else if ("异常".equals(actualStatus)) {
                errorNodes++;
                unhealthyList.add(buildUnhealthyNodeInfo(node, actualStatus, isCore, deviceData));
                if (!"异常".equals(node.getNodeStatus())) {
                    node.setNodeStatus("异常");
                    node.setUpdateTime(LocalDateTime.now());
                    chainNodeMapper.updateById(node);
                }
            } else {
                offlineNodes++;
                unhealthyList.add(buildUnhealthyNodeInfo(node, "离线", isCore, deviceData));
                if (!"离线".equals(node.getNodeStatus())) {
                    node.setNodeStatus("离线");
                    node.setUpdateTime(LocalDateTime.now());
                    chainNodeMapper.updateById(node);
                }
            }
        }

        // 5. 计算健康度评分
        int healthScore = calculateHealthScore(totalNodes, onlineNodes, offlineNodes, errorNodes,
                coreTotalNodes, coreOnlineNodes);
        String healthLevel = calculateHealthLevel(healthScore);

        // 6. 更新链路状态
        int status;
        if (totalNodes == 0) {
            status = 0;
        } else if (onlineNodes == totalNodes) {
            status = 1; // 全部在线
        } else if (errorNodes > 0 || onlineNodes == 0) {
            status = 2; // 有异常或全离线
        } else {
            status = 1; // 部分在线
        }

        LocalDateTime now = LocalDateTime.now();
        config.setStatus(status);
        config.setHealthScore(healthScore);
        config.setOnlineNodes(onlineNodes);
        config.setOfflineNodes(offlineNodes);
        config.setErrorNodes(errorNodes);
        config.setLastHealthCheckTime(now);
        config.setUpdateTime(now);
        chainConfigMapper.updateById(config);

        // 7. 构建返回结果
        ChainHealthVO healthVO = new ChainHealthVO();
        healthVO.setChainId(chainId);
        healthVO.setChainName(config.getChainName());
        healthVO.setChainCode(config.getChainCode());
        healthVO.setHealthScore(healthScore);
        healthVO.setHealthLevel(healthLevel);
        healthVO.setStatus(status);
        healthVO.setTotalNodes(totalNodes);
        healthVO.setOnlineNodes(onlineNodes);
        healthVO.setOfflineNodes(offlineNodes);
        healthVO.setErrorNodes(errorNodes);
        healthVO.setCoreOnlineNodes(coreOnlineNodes);
        healthVO.setCoreTotalNodes(coreTotalNodes);
        healthVO.setUnhealthyNodes(unhealthyList);
        healthVO.setCheckTime(now);
        healthVO.setCheckDurationMs(System.currentTimeMillis() - startTime);
        healthVO.setEnabled(config.getEnabled());

        log.info("链路健康度检查完成: chainId={}, 健康度={}/{}, 等级={}, 在线={}, 离线={}, 异常={}, 耗时={}ms",
                chainId, healthScore, 100, healthLevel, onlineNodes, offlineNodes, errorNodes,
                healthVO.getCheckDurationMs());
        reportUnhealthyChain(healthVO);
        return healthVO;
    }

    private void reportUnhealthyChain(ChainHealthVO healthVO) {
        if (diagnosticLogReporter == null || healthVO == null) {
            return;
        }
        Integer healthScore = healthVO.getHealthScore();
        Integer offlineNodes = healthVO.getOfflineNodes();
        Integer errorNodes = healthVO.getErrorNodes();
        boolean unhealthy = (healthScore != null && healthScore < 70)
                || (offlineNodes != null && offlineNodes > 0)
                || (errorNodes != null && errorNodes > 0);
        if (!unhealthy) {
            return;
        }

        DiagnosticLogReport report = new DiagnosticLogReport();
        report.setEventType("CHAIN_HEALTH_ABNORMAL");
        report.setEventLevel((healthScore != null && healthScore < 50) ? "error" : "warn");
        report.setStage("content");
        report.setServiceName("monitor-platform-forward");
        report.setChainId(healthVO.getChainId());
        report.setChainCode(healthVO.getChainCode());
        report.setResultStatus("fail");
        report.setSummary("chain health abnormal: score=" + healthScore
                + ", offline=" + offlineNodes + ", error=" + errorNodes);
        report.setErrorMessage("unhealthy nodes=" + (healthVO.getUnhealthyNodes() == null ? 0 : healthVO.getUnhealthyNodes().size()));
        report.setDetailJson(JSON.toJSONString(healthVO.getUnhealthyNodes()));
        report.setRefTable("task_chain_config");
        report.setRefId(healthVO.getChainId() == null ? null : String.valueOf(healthVO.getChainId()));
        report.setDedupKey("CHAIN_HEALTH_ABNORMAL:" + healthVO.getChainId());
        diagnosticLogReporter.reportAsync(report);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer checkAllChainHealth() {
        log.info("开始批量链路健康度检查");

        LambdaQueryWrapper<TaskChainConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskChainConfig::getDeleted, 0)
                .eq(TaskChainConfig::getEnabled, 1); // 仅检查启用的链路
        List<TaskChainConfig> allChains = chainConfigMapper.selectList(wrapper);

        int checkCount = 0;
        int healthyCount = 0;
        int unhealthyCount = 0;

        for (TaskChainConfig chain : allChains) {
            try {
                ChainHealthVO health = checkChainHealth(chain.getId());
                checkCount++;
                if (health.getHealthScore() >= 70) {
                    healthyCount++;
                } else {
                    unhealthyCount++;
                }
            } catch (Exception e) {
                log.error("链路健康度检查失败: chainId={}", chain.getId(), e);
            }
        }

        log.info("批量链路健康度检查完成: 总数={}, 成功={}, 健康={}, 不健康={}",
                allChains.size(), checkCount, healthyCount, unhealthyCount);
        return checkCount;
    }

    /**
     * 探测单个设备的实际在线状态
     * 优先通过设备服务查询，失败时回退到节点存储的状态
     *
     * @return Map 包含：
     *   - "status":     状态字符串（"在线" / "异常" / "离线"）
     *   - "deviceData": 设备详情 Map（来自设备服务，可能为 null）
     */
    private Map<String, Object> probeDeviceStatus(TaskChainNode node) {
        Map<String, Object> result = new HashMap<>();

        if (restTemplate == null) {
            // RestTemplate未配置，回退到节点存储状态
            result.put("status", node.getNodeStatus() != null ? node.getNodeStatus() : "离线");
            result.put("deviceData", null);
            return result;
        }

        try {
            String url = deviceServiceUrl + "/device/unified/" + node.getDeviceType()
                    + "/" + node.getDeviceId() + "/detail";
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response != null && Integer.valueOf(200).equals(response.get("code"))) {
                Object data = response.get("data");
                if (data instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> deviceData = (Map<String, Object>) data;
                    String deviceStatus = String.valueOf(deviceData.get("status"));

                    if ("在线".equals(deviceStatus)) {
                        result.put("status", "在线");
                    } else if ("告警".equals(deviceStatus)) {
                        result.put("status", "异常");
                    } else {
                        result.put("status", "离线");
                    }
                    // 无论状态如何，都保留设备详情供异常节点使用
                    result.put("deviceData", deviceData);
                    return result;
                }
            }
            result.put("status", "离线");
            result.put("deviceData", null);
            return result;
        } catch (Exception e) {
            log.debug("设备状态探测失败，回退到存储状态: deviceId={}, error={}",
                    node.getDeviceId(), e.getMessage());
            // 探测失败时回退到节点存储的状态
            result.put("status", node.getNodeStatus() != null ? node.getNodeStatus() : "离线");
            result.put("deviceData", null);
            return result;
        }
    }

    /**
     * 计算健康度评分（0-100分）
     * 核心节点权重1.5，可选节点权重0.5，普通节点权重1.0
     */
    private int calculateHealthScore(int totalNodes, int onlineNodes, int offlineNodes,
                                     int errorNodes, int coreTotalNodes, int coreOnlineNodes) {
        if (totalNodes == 0) {
            return 0;
        }

        // 简化计算：基础分 = 在线比例 * 100
        double baseScore = (double) onlineNodes / totalNodes * 100;

        // 核心节点奖励分：核心节点全部在线额外奖励（最高10分）
        double coreBonus = 0;
        if (coreTotalNodes > 0 && coreOnlineNodes == coreTotalNodes) {
            coreBonus = 10;
        } else if (coreTotalNodes > 0) {
            coreBonus = (double) coreOnlineNodes / coreTotalNodes * 10;
        }

        // 异常节点扣分：每个异常节点额外扣5分
        double errorPenalty = errorNodes * 5;

        double score = baseScore + coreBonus - errorPenalty;
        return Math.max(0, Math.min(100, (int) Math.round(score)));
    }

    /**
     * 根据健康度评分计算健康等级
     */
    private String calculateHealthLevel(int healthScore) {
        if (healthScore >= 90) return "A";
        if (healthScore >= 70) return "B";
        if (healthScore >= 50) return "C";
        if (healthScore >= 30) return "D";
        return "F";
    }

    /**
     * 判断是否为核心节点类型
     */
    private boolean isCoreNodeType(String deviceType) {
        return MAIN_NODE_TYPES.contains(deviceType) || BRANCH_CORE_TYPES.contains(deviceType);
    }

    /**
     * 构建不健康节点信息
     *
     * @param node       链路节点
     * @param status     节点状态（"离线" / "异常"）
     * @param isCore     是否为核心节点
     * @param deviceData 设备服务返回的详情 Map（可为 null），用于填充异常节点的告警详情
     */
    private ChainHealthVO.UnhealthyNodeInfo buildUnhealthyNodeInfo(
            TaskChainNode node, String status, boolean isCore, Map<String, Object> deviceData) {
        ChainHealthVO.UnhealthyNodeInfo info = new ChainHealthVO.UnhealthyNodeInfo();
        info.setNodeId(node.getId());
        info.setDeviceId(node.getDeviceId());
        info.setDeviceName(node.getDeviceName());
        info.setDeviceType(node.getDeviceType());
        info.setDeviceTypeDesc(getDeviceTypeDesc(node.getDeviceType()));
        info.setDeviceIp(node.getDeviceIp());
        info.setNodeStatus(status);
        info.setCoreNode(isCore);

        switch (status) {
            case "离线":
                info.setStatusDesc("设备无响应或已断开连接");
                break;
            case "异常":
                info.setStatusDesc("设备状态异常，请检查设备运行情况");
                // 填充异常节点的告警/异常详情
                fillErrorDetail(info, deviceData);
                break;
            default:
                info.setStatusDesc("未知状态");
        }
        return info;
    }

    /**
     * 从设备服务返回的 deviceData 中提取异常/告警详情，填充到 UnhealthyNodeInfo
     */
    private void fillErrorDetail(ChainHealthVO.UnhealthyNodeInfo info, Map<String, Object> deviceData) {
        if (deviceData == null) {
            return;
        }
        try {
            // 告警次数
            Object alarmCountObj = deviceData.get("alarmCount");
            if (alarmCountObj != null) {
                info.setAlarmCount(Integer.valueOf(alarmCountObj.toString()));
            }
            // 最后告警时间
            Object lastAlarmTimeObj = deviceData.get("lastAlarmTime");
            if (lastAlarmTimeObj != null) {
                info.setLastAlarmTime(String.valueOf(lastAlarmTimeObj));
            }
            // 最后告警类型
            Object lastAlarmTypeObj = deviceData.get("lastAlarmType");
            if (lastAlarmTypeObj != null) {
                info.setLastAlarmType(String.valueOf(lastAlarmTypeObj));
            }

            // 组装异常详情描述
            StringBuilder detailBuilder = new StringBuilder();
            String deviceStatus = deviceData.get("status") != null ? String.valueOf(deviceData.get("status")) : null;
            if (deviceStatus != null && !"null".equals(deviceStatus)) {
                detailBuilder.append("设备状态: ").append(deviceStatus);
            }
            if (info.getAlarmCount() != null && info.getAlarmCount() > 0) {
                if (detailBuilder.length() > 0) {
                    detailBuilder.append("; ");
                }
                detailBuilder.append("累计告警 ").append(info.getAlarmCount()).append(" 次");
            }
            if (info.getLastAlarmType() != null && !"null".equals(info.getLastAlarmType())) {
                if (detailBuilder.length() > 0) {
                    detailBuilder.append("; ");
                }
                detailBuilder.append("最近告警类型: ").append(info.getLastAlarmType());
            }
            if (info.getLastAlarmTime() != null && !"null".equals(info.getLastAlarmTime())) {
                if (detailBuilder.length() > 0) {
                    detailBuilder.append("; ");
                }
                detailBuilder.append("最近告警时间: ").append(info.getLastAlarmTime());
            }

            // 备注信息（设备服务可能返回 remark）
            Object remarkObj = deviceData.get("remark");
            if (remarkObj != null && !"null".equals(String.valueOf(remarkObj))
                    && !String.valueOf(remarkObj).trim().isEmpty()) {
                if (detailBuilder.length() > 0) {
                    detailBuilder.append("; ");
                }
                detailBuilder.append("备注: ").append(remarkObj);
            }

            if (detailBuilder.length() > 0) {
                info.setErrorDetail(detailBuilder.toString());
                // 同时用更具体的描述覆盖 statusDesc
                info.setStatusDesc(detailBuilder.toString());
            }
        } catch (Exception e) {
            log.debug("填充异常详情失败: deviceId={}, error={}", info.getDeviceId(), e.getMessage());
        }
    }
}
