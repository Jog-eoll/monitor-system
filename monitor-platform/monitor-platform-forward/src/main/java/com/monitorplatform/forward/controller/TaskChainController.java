package com.monitorplatform.forward.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitorplatform.common.entity.Result;
import com.monitorplatform.forward.entity.dto.*;
import com.monitorplatform.forward.entity.vo.*;
import com.monitorplatform.forward.service.TaskChainService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 任务链路配置管理Controller
 */
@Slf4j
@RestController
@RequestMapping("/chain")

public class TaskChainController {

    @Resource
    private TaskChainService taskChainService;

    /**
     * 创建任务链路
     */
    @PostMapping("/create")
    public Result<?> createChain(@Validated @RequestBody CreateChainDTO dto) {
        try {
            log.info("创建任务链路请求: {}", dto.getChainCode());
            Long chainId = taskChainService.createChain(dto);

            // 链路创建后自动触发健康度检查
            try {
                taskChainService.checkChainHealth(chainId);
                log.info("链路创建后健康度检查已触发: chainId={}", chainId);
            } catch (Exception ex) {
                log.warn("链路创建后健康度检查失败（不影响链路创建）: chainId={}, error={}",
                        chainId, ex.getMessage());
            }

            return Result.success("链路创建成功", Result.buildData("chainId", chainId));
        } catch (Exception e) {
            log.error("创建链路失败", e);
            return Result.error("创建链路失败: " + e.getMessage());
        }
    }


    /**
     * 新建任务
     */
    @PostMapping("/create/basic")
    public Result<?> createChainBasic(@Validated @RequestBody CreateChainBasicDTO dto){
        try {
            CreateChainDTO createChainDTO = new CreateChainDTO();

            createChainDTO.setChainName(dto.getChainName());
            createChainDTO.setChainCode(dto.getChainCode());
            createChainDTO.setEnabled(dto.getEnabled());
            createChainDTO.setCreatedBy(dto.getCreatedBy());
            createChainDTO.setRemark(dto.getRemark());
            createChainDTO.setRootNodes(new java.util.ArrayList<>());

            Long chainId = taskChainService.createChain(createChainDTO);
            return Result.success("链路基本信息创建成功", Result.buildData("chainId", chainId));
        } catch (Exception e) {
            log.error("创建链路基本信息失败", e);
            return Result.error("创建链路基本信息失败: " + e.getMessage());
        }
    }

    /**
     * 创建任务的链路
     */
    @PostMapping("/create/nodes")
    public Result<?> createChainNodes(@Validated @RequestBody CreateChainNodesDTO dto) {
        try {
            log.info("为链路创建节点拓扑: chainId={}", dto.getChainId());
            Boolean success = taskChainService.createChainNodes(dto);
            return Result.success("节点拓扑创建成功", success);
        } catch (Exception e) {
            log.error("创建节点拓扑失败", e);
            return Result.error("创建节点拓扑失败: " + e.getMessage());
        }
    }

    /**
     * 更新任务链路
     */
    @PostMapping("/update")
    public Result<?> updateChain(@Validated @RequestBody UpdateChainDTO dto) {
        try {
            log.info("更新任务链路请求: {}", dto.getId());
            Boolean success = taskChainService.updateChain(dto);
            return Result.success("链路更新成功", success);
        } catch (Exception e) {
            log.error("更新链路失败", e);
            return Result.error("更新链路失败: " + e.getMessage());
        }
    }

    /**
     * 删除任务链路（逻辑删除）
     */
    @DeleteMapping("/delete/{chainId}")
    public Result<?> deleteChain(@PathVariable Long chainId) {
        try {
            log.info("删除任务链路请求: {}", chainId);
            Boolean success = taskChainService.deleteChain(chainId);
            return Result.success("链路删除成功", success);
        } catch (Exception e) {
            log.error("删除链路失败", e);
            return Result.error("删除链路失败: " + e.getMessage());
        }
    }

    /**
     * 查询链路详情（基本信息，不包含节点树）
     */
    @PostMapping ("/detail/{chainId}")
    public Result<?> getChainDetail(@PathVariable Long chainId) {
        try {
            log.info("查询链路详情: {}", chainId);
            TaskChainVO vo = taskChainService.getChainById(chainId);
            if (vo == null) {
                return Result.error("链路不存在");
            }
            return Result.success(vo);
        } catch (Exception e) {
            log.error("查询链路详情失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 查询链路详情
     */
    @GetMapping("/tree/{chainId}")
    public Result<?> getChainTree(@PathVariable Long chainId) {
        try {
            log.info("查询链路树形结构: {}", chainId);
            TaskChainVO vo = taskChainService.getChainTree(chainId);
            return Result.success(vo);
        } catch (Exception e) {
            log.error("查询链路树失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 分页查询链路列表
     */
    @PostMapping("/page")
    public Result<?> queryChainPage(@RequestBody ChainQueryDTO queryDTO) {
        try {
            log.info("分页查询链路列表: {}", queryDTO);
            Page<TaskChainVO> page = taskChainService.queryChainPage(queryDTO);
            return Result.success(page);
        } catch (Exception e) {
            log.error("查询链路列表失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 校验链路合法性
     */
    @PostMapping("/validate/{chainId}")
    public Result<?> validateChain(@PathVariable Long chainId) {
        try {
            log.info("校验链路: {}", chainId);
            Boolean isValid = taskChainService.validateChain(chainId);
            return Result.success("校验完成", Result.buildData("isValid", isValid));
        } catch (Exception e) {
            log.error("校验链路失败", e);
            return Result.error("校验失败: " + e.getMessage());
        }
    }

    /**
     * 校验链路结构（业务规则校验）
     */
    @PostMapping("/validate/structure/{chainId}")
    public Result<?> validateChainStructure(@PathVariable Long chainId) {
        try {
            log.info("校验链路结构: {}", chainId);
            Boolean isValid = taskChainService.validateChainStructure(chainId);
            return Result.success("校验成功", Result.buildData("isValid", isValid));
        } catch (Exception e) {
            log.error("校验链路结构失败", e);
            return Result.error("校验失败: " + e.getMessage());
        }
    }

    /**
     * 同步链路状态
     */
    @PostMapping("/sync/status/{chainId}")
    public Result<?> syncChainStatus(@PathVariable Long chainId) {
        try {
            log.info("同步链路状态: {}", chainId);
            Boolean success = taskChainService.syncChainStatus(chainId);
            return Result.success("状态同步成功", success);
        } catch (Exception e) {
            log.error("同步链路状态失败", e);
            return Result.error("同步失败: " + e.getMessage());
        }
    }

    /**
     * 批量同步所有链路状态
     */
    @PostMapping("/sync/all")
    public Result<?> syncAllChainStatus() {
        try {
            log.info("批量同步所有链路状态");
            Integer count = taskChainService.syncAllChainStatus();
            return Result.success("批量同步完成", Result.buildData("syncCount", count));
        } catch (Exception e) {
            log.error("批量同步失败", e);
            return Result.error("批量同步失败: " + e.getMessage());
        }
    }

    /**
     * 查询设备占用情况
     */
    @PostMapping("/device/{deviceId}")
    public Result<?> queryChainsByDevice(@PathVariable String deviceId) {
        try {
            log.info("查询设备占用情况: {}", deviceId);
            List<TaskChainVO> chains = taskChainService.queryChainsByDevice(deviceId);
            return Result.success(chains);
        } catch (Exception e) {
            log.error("查询设备占用情况失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 检查设备是否可用
     */
    @PostMapping("/device/check/{deviceId}")
    public Result<?> checkDeviceAvailable(@PathVariable String deviceId) {
        try {
            log.info("检查设备可用性: {}", deviceId);
            Boolean available = taskChainService.checkDeviceAvailable(deviceId);
            return Result.success(Result.buildData("available", available));
        } catch (Exception e) {
            log.error("检查设备可用性失败", e);
            return Result.error("检查失败: " + e.getMessage());
        }
    }






    // ==================== 树形节点操作 ====================

    /**
     * 创建节点（树形结构）
     * POST /chain/node/create
     * 
     * 请求体示例:
     * {
     *   "chainId": 1,
     *   "parentId": null,  // 根节点为null，否则填写父节点ID
     *   "deviceId": "PUB-SRV-001",
     *   "deviceName": "发布服务器01",
     *   "deviceType": "publish_server",
     *   "remark": "核心节点"
     * }
     */
    @PostMapping("/node/create")
    public Result<?> createNode(@Validated @RequestBody Map<String, Object> params) {
        try {
            log.info("创建节点请求: {}", params);
            Long chainId = Long.parseLong(params.get("chainId").toString());
            Long parentId = params.containsKey("parentId") && params.get("parentId") != null 
                    ? Long.parseLong(params.get("parentId").toString()) 
                    : null;
            
            // 解析节点数据
            ChainNodeDTO nodeDTO = new ChainNodeDTO();
            nodeDTO.setDeviceId(params.get("deviceId").toString());
            nodeDTO.setDeviceName(params.get("deviceName").toString());
            nodeDTO.setDeviceType(params.get("deviceType").toString());
            if (params.containsKey("deviceIp")) {
                nodeDTO.setDeviceIp(params.get("deviceIp").toString());
            }
            if (params.containsKey("remark")) {
                nodeDTO.setRemark(params.get("remark").toString());
            }
            
            Long nodeId = taskChainService.createNode(chainId, parentId, nodeDTO);
            return Result.success("创建节点成功", Result.buildData("nodeId", nodeId));
        } catch (Exception e) {
            log.error("创建节点失败", e);
            return Result.error("创建节点失败: " + e.getMessage());
        }
    }

    /**
     * 移动节点到新的父节点下
     * PUT /chain/node/move
     * 
     * 请求体示例:
     * {
     *   "nodeId": 10,
     *   "newParentId": 5  // null表示移动到根层级
     * }
     */
    @PutMapping("/node/move")
    public Result<?> moveNode(@Validated @RequestBody Map<String, Object> params) {
        try {
            log.info("移动节点请求: {}", params);
            Long nodeId = Long.parseLong(params.get("nodeId").toString());
            Long newParentId = params.containsKey("newParentId") && params.get("newParentId") != null
                    ? Long.parseLong(params.get("newParentId").toString())
                    : null;
            
            Boolean success = taskChainService.moveNode(nodeId, newParentId);
            return Result.success("移动节点成功", success);
        } catch (Exception e) {
            log.error("移动节点失败", e);
            return Result.error("移动节点失败: " + e.getMessage());
        }
    }

    // ==================== 节点操作 ====================

    /**
     * 更新单个节点配置
     * PUT /forward/chain/node/update
     * 
     * 请求体示例:
     * {
     *   "nodeId": 10,
     *   "deviceName": "内容识别服务器01更新",
     *   "remark": "升级为核心节点"
     * }
     */
    @PutMapping("/node/update")
    public Result<?> updateNode(@Validated @RequestBody Map<String, Object> params) {
        try {
            log.info("更新节点请求: {}", params);
            Long nodeId = Long.parseLong(params.get("nodeId").toString());
            
            // 解析节点数据（只更新提供的字段）
            ChainNodeDTO nodeDTO = new ChainNodeDTO();
            if (params.containsKey("deviceId")) {
                nodeDTO.setDeviceId(params.get("deviceId").toString());
            }
            if (params.containsKey("deviceName")) {
                nodeDTO.setDeviceName(params.get("deviceName").toString());
            }
            if (params.containsKey("deviceType")) {
                nodeDTO.setDeviceType(params.get("deviceType").toString());
            }
            if (params.containsKey("deviceIp")) {
                nodeDTO.setDeviceIp(params.get("deviceIp").toString());
            }
            if (params.containsKey("remark")) {
                nodeDTO.setRemark(params.get("remark").toString());
            }
            
            Boolean success = taskChainService.updateNode(nodeId, nodeDTO);
            return Result.success("更新节点成功", success);
        } catch (Exception e) {
            log.error("更新节点失败", e);
            return Result.error("更新节点失败: " + e.getMessage());
        }
    }

    /**
     * 删除单个节点
     * DELETE /forward/chain/node/delete/{nodeId}
     */
    @DeleteMapping("/node/delete/{nodeId}")
    public Result<?> deleteNode(@PathVariable Long nodeId) {
        try {
            log.info("删除节点请求: nodeId={}", nodeId);
            Boolean success = taskChainService.deleteNode(nodeId);
            return Result.success("删除节点成功", success);
        } catch (Exception e) {
            log.error("删除节点失败", e);
            return Result.error("删除节点失败: " + e.getMessage());
        }
    }

    /**
     * 查询节点详情
     * GET /forward/chain/node/{nodeId}
     */
    @GetMapping("/node/{nodeId:\\d+}")
    public Result<?> getNodeById(@PathVariable Long nodeId) {
        try {
            log.info("查询节点详情: nodeId={}", nodeId);
            TaskChainNodeVO node = taskChainService.getNodeById(nodeId);
            return Result.success("查询成功", node);
        } catch (Exception e) {
            log.error("查询节点失败", e);
            return Result.error("查询节点失败: " + e.getMessage());
        }
    }

    /**
     * 手动下发链路配置到网关
     * POST /chain/deploy/{chainId}
     * 
     * 功能说明：
     * - 手动触发配置下发（与自动下发相同的逻辑，但由用户主动触发）
     * - 用于配置修改后重新下发、下发失败后重试等场景
     * - 会同时下发到发布网关和终端网关
     * 
     * 返回示例：
     * {
     *   "code": 200,
     *   "message": "配置下发成功",
     *   "data": {
     *     "chainId": 20,
     *     "totalBranches": 1,
     *     "publishGateway": { "successCount": 1, "failedCount": 0 },
     *     "terminalGateway": { "successCount": 1, "failedCount": 0 },
     *     "details": [...]
     *   }
     * }
     */
    @PostMapping("/deploy/{chainId}")
    public Result<?> deployChainConfig(@PathVariable Long chainId) {
        try {
            log.info("==========================================");
            log.info("  【手动下发】用户触发配置下发");
            log.info("  链路ID: {}", chainId);
            log.info("==========================================");
            
            // 调用下发服务
            DeploySummaryVO deploySummaryVO = taskChainService.manualDeployConfig(chainId);
            
            if (deploySummaryVO == null) {
                return Result.error("配置下发失败：未返回下发结果");
            }

            // DeploySummaryVO 中的统计字段已经是 VO 类型，不能再按 Map 强转。
            Integer totalBranches = defaultInt(deploySummaryVO.getTotalBranches());
            int publishSuccess = successCount(deploySummaryVO.getPublishGateway());
            int publishFailed = failedCount(deploySummaryVO.getPublishGateway());
            int terminalSuccess = successCount(deploySummaryVO.getTerminalGateway());
            int terminalFailed = failedCount(deploySummaryVO.getTerminalGateway());
            
            log.info("==========================================");
            log.info("  【手动下发】配置下发完成");
            log.info("  分支总数: {}", totalBranches);
            log.info("  发布网关 - 成功: {}, 失败: {}", publishSuccess, publishFailed);
            log.info("  终端网关 - 成功: {}, 失败: {}", terminalSuccess, terminalFailed);
            log.info("==========================================");
            
            // 判断是否全部成功
            if (publishFailed == 0 && terminalFailed == 0) {
                return Result.success("配置下发成功", deploySummaryVO);
            } else if (publishSuccess > 0 || terminalSuccess > 0) {
                return Result.success("配置下发部分成功", deploySummaryVO);
            } else {
                return Result.error("配置下发失败");
            }
            
        } catch (Exception e) {
            log.error("【手动下发】配置下发异常: chainId={}", chainId, e);
            return Result.error("配置下发失败: " + e.getMessage());
        }
    }

    private int successCount(DeployGatewayStatsVO stats) {
        if (stats == null || stats.getSuccessCount() == null) {
            return 0;
        }
        return stats.getSuccessCount();
    }

    private int failedCount(DeployGatewayStatsVO stats) {
        if (stats == null || stats.getFailedCount() == null) {
            return 0;
        }
        return stats.getFailedCount();
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }
    
    /**
     * 停止终端网关代理规则（报警应急处理）
     * 
     * POST /chain/stop-gateway/{chainId}
     * 
     * 功能说明：
     * - 用于报警触发后紧急停止指定链路的终端网关代理
     * - 自动查询链路的终端网关信息
     * - 调用终端网关的停止接口
     * 
     * 请求体示例：
     * {
     *   "reason": "报警触发，紧急切断连接",
     *   "operator": "admin"
     * }
     * 
     * 返回示例：
     * {
     *   "code": 200,
     *   "message": "成功停止网关代理",
     *   "data": {
     *     "chainId": 22,
     *     "ruleId": "22_terminal",
     *     "gatewayIp": "192.168.1.100",
     *     "gatewayPort": 8093,
     *     "stopped": true
     *   }
     * }
     */
    @PostMapping("/stop-gateway/{chainId}")
    public Result<?> stopGatewayProxy(@PathVariable Long chainId, 
                                                 @RequestBody(required = false) StopGatewayProxyRequestDTO params) {
        try {
            log.info("==========================================");
            log.info("  【停止网关】收到停止网关代理请求");
            log.info("  链路ID: {}", chainId);
            if (params != null) {
                log.info("  原因: {}", params.getReason());
                log.info("  操作人: {}", params.getOperator());
            }
            log.info("==========================================");
            
            // 调用service层方法停止网关代理
            StopGatewayProxyResultVO stopResult = taskChainService.stopTerminalGatewayProxy(chainId, params);
            
            Boolean success = stopResult.getSuccess();
            if (Boolean.TRUE.equals(success)) {
                log.info("✅ 停止网关代理成功");
                return Result.success("成功停止网关代理", stopResult);
            } else {
                log.warn("❌ 停止网关代理失败: {}", stopResult.getMessage());
                return Result.error(String.valueOf(stopResult.getMessage()));
            }
            
        } catch (Exception e) {
            log.error("停止网关代理异常: chainId={}", chainId, e);
            return Result.error("停止网关代理失败: " + e.getMessage());
        }
    }

    /**
     * 通过情报板 IP 查询终端网关信息
     * GET /chain/gateway-by-board?boardIp=xxx
     */
    @GetMapping("/gateway-by-board")
    public Result<?> getGatewayByBoardIp(@RequestParam String boardIp) {
        try {
            log.info("查询终端网关: boardIp={}", boardIp);
            Map<String, Object> result = taskChainService.getTerminalGatewayByBoardIp(boardIp);
            if (Boolean.TRUE.equals(result.get("success"))) {
                return Result.data(result, "查询成功");
            } else {
                return Result.fail(400, String.valueOf(result.get("message")));
            }
        } catch (Exception e) {
            log.error("查询终端网关失败: boardIp={}", boardIp, e);
            return Result.fail(500, "查询失败: " + e.getMessage());
        }
    }

    /**
     * 通过链路 ID 查询情报板 IP
     * GET /chain/info-board?chainId=xxx
     */
    @GetMapping("/info-board")
    public Result<?> getInfoBoardByChainId(@RequestParam Long chainId) {
        try {
            log.info("查询情报板IP: chainId={}", chainId);
            Map<String, Object> result = taskChainService.getInfoBoardByChainId(chainId);
            if (Boolean.TRUE.equals(result.get("success"))) {
                return Result.data(result, "查询成功");
            } else {
                return Result.fail(400, String.valueOf(result.get("message")));
            }
        } catch (Exception e) {
            log.error("查询情报板IP失败: chainId={}", chainId, e);
            return Result.fail(500, "查询失败: " + e.getMessage());
        }
    }

    /**
     * 启用/停用链路
     * 1. 更新管控平台 task_chain_config 的 enabled/status 字段
     * 2. 向发布网关、终端网关下发 ENABLE/DISABLE 指令
     *
     * POST /forward/chain/enable/{chainId}?enabled=1
     * POST /forward/chain/enable/{chainId}?enabled=0
     */
    @PostMapping("/enable/{chainId}")
    public Result<?> setChainEnabled(@PathVariable Long chainId,
                                               @RequestParam Integer enabled) {
        try {
            log.info("启用/停用链路: chainId={}, enabled={}", chainId, enabled);
            if (enabled != 0 && enabled != 1) {
                return Result.error("参数 enabled 必须为 0 或 1");
            }
            Boolean result = taskChainService.setChainEnabled(chainId, enabled);
            return Result.success(enabled == 1 ? "链路已启用" : "链路已停用", result);
        } catch (Exception e) {
            log.error("启用/停用链路失败: chainId={}, enabled={}", chainId, enabled, e);
            return Result.error("操作失败: " + e.getMessage());
        }
    }

    // ==================== 链路健康度监控 ====================

    /**
     * 检查单条链路的健康度
     * GET /chain/health/{chainId}
     *
     * 功能说明：
     * - 遍历链路所有节点，探测设备实际在线状态
     * - 计算健康度评分（0-100分）和健康等级（A/B/C/D/F）
     * - 返回不健康节点详情，便于快速定位故障
     *
     * 返回示例：
     * {
     *   "code": 200,
     *   "message": "健康度检查完成",
     *   "data": {
     *     "chainId": 1,
     *     "chainName": "链路 A",
     *     "healthScore": 85,
     *     "healthLevel": "B",
     *     "totalNodes": 5,
     *     "onlineNodes": 4,
     *     "offlineNodes": 1,
     *     "errorNodes": 0,
     *     "unhealthyNodes": [...]
     *   }
     * }
     */
    @GetMapping("/health/{chainId:\\d+}")
    public Result<?> checkChainHealth(@PathVariable Long chainId) {
        try {
            log.info("链路健康度检查请求: chainId={}", chainId);
            ChainHealthVO healthVO = taskChainService.checkChainHealth(chainId);

            String message = String.format("健康度检查完成: %s分(%s级), 在线%d/总%d",
                    healthVO.getHealthScore(), healthVO.getHealthLevel(),
                    healthVO.getOnlineNodes(), healthVO.getTotalNodes());

            return Result.success(message, healthVO);
        } catch (Exception e) {
            log.error("链路健康度检查失败: chainId={}", chainId, e);
            return Result.error("健康度检查失败: " + e.getMessage());
        }
    }

    /**
     * 批量检查所有启用链路的健康度
     * POST /chain/health/all
     *
     * 功能说明：
     * - 遍历所有启用状态的链路
     * - 对每条链路执行健康度检查
     * - 用于手动触发全局巡检
     */
    @PostMapping("/health/all")
    public Result<?> checkAllChainHealth() {
        try {
            log.info("批量链路健康度检查请求");
            Integer count = taskChainService.checkAllChainHealth();
            return Result.success("批量健康度检查完成",
                    Result.buildData("checkCount", count));
        } catch (Exception e) {
            log.error("批量链路健康度检查失败", e);
            return Result.error("批量检查失败: " + e.getMessage());
        }
    }
}
