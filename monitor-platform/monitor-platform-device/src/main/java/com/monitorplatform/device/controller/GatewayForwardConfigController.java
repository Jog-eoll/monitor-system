package com.monitorplatform.device.controller;

import com.monitorplatform.common.entity.Result;
import com.monitorplatform.device.entity.dto.ForwardChannelConfigDTO;
import com.monitorplatform.device.service.GatewayForwardConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

import java.util.HashMap;
import java.util.Map;

/**
 * 网关转发配置管理控制器
 * 管控平台通过此接口向加密网关下发转发配置
 */
@Slf4j
@RestController
@RequestMapping("/device/gateway/forward-config")
public class GatewayForwardConfigController {

    @Resource
    private GatewayForwardConfigService gatewayForwardConfigService;

    /**
     * 下发转发配置到指定网关
     * @param dto 转发配置信息
     * @return 操作结果
     */
    @PostMapping("/deploy")
    public Result<?> deployConfig(@Validated @RequestBody ForwardChannelConfigDTO dto) {
        try {
            log.info("管控平台下发转发配置到网关: {}, 通道: {}", dto.getGatewaySn(), dto.getChannelName());
            boolean result = gatewayForwardConfigService.deployConfigToGateway(dto);
            return result ? Result.success("配置下发成功") : Result.error("配置下发失败");
        } catch (IllegalArgumentException e) {
            log.error("配置参数错误: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("下发配置失败", e);
            return Result.error("下发配置失败: " + e.getMessage());
        }
    }

    /**
     * 批量下发配置到指定网关
     * @param gatewaySn 网关序列号
     * @param configs 配置列表
     * @return 操作结果
     */
    @PostMapping("/batch-deploy/{gatewaySn}")
    public Result<?> batchDeployConfigs(
            @PathVariable String gatewaySn,
            @RequestBody ForwardChannelConfigDTO[] configs) {
        try {
            log.info("管控平台批量下发转发配置到网关: {}, 数量: {}", gatewaySn, configs.length);
            int successCount = gatewayForwardConfigService.batchDeployConfigs(gatewaySn, configs);
            Map<String, Object> data = new HashMap<>();
            data.put("total", configs.length);
            data.put("success", successCount);
            data.put("failed", configs.length - successCount);
            return Result.data(data);
        } catch (Exception e) {
            log.error("批量下发配置失败", e);
            return Result.error("批量下发配置失败: " + e.getMessage());
        }
    }

    /**
     * 更新网关转发配置
     * @param dto 转发配置信息
     * @return 操作结果
     */
    @PutMapping("/update")
    public Result<?> updateConfig(@Validated @RequestBody ForwardChannelConfigDTO dto) {
        try {
            if (dto.getId() == null) {
                return Result.error("配置ID不能为空");
            }
            log.info("管控平台更新网关转发配置: ID={}, 网关={}", dto.getId(), dto.getGatewaySn());
            boolean result = gatewayForwardConfigService.updateConfigToGateway(dto);
            return result ? Result.success("配置更新成功") : Result.error("配置更新失败");
        } catch (IllegalArgumentException e) {
            log.error("配置参数错误: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("更新配置失败", e);
            return Result.error("更新配置失败: " + e.getMessage());
        }
    }

    /**
     * 删除网关转发配置
     * @param gatewaySn 网关序列号
     * @param configId 配置ID
     * @return 操作结果
     */
    @DeleteMapping("/{gatewaySn}/{configId}")
    public Result<?> deleteConfig(
            @PathVariable String gatewaySn,
            @PathVariable Long configId) {
        try {
            log.info("管控平台删除网关转发配置: 网关={}, 配置ID={}", gatewaySn, configId);
            boolean result = gatewayForwardConfigService.deleteConfigFromGateway(gatewaySn, configId);
            return result ? Result.success("配置删除成功") : Result.error("配置删除失败");
        } catch (Exception e) {
            log.error("删除配置失败", e);
            return Result.error("删除配置失败: " + e.getMessage());
        }
    }

    /**
     * 启动/停止网关转发通道
     * @param gatewaySn 网关序列号
     * @param configId 配置ID
     * @param action 操作类型：start-启动, stop-停止, restart-重启
     * @return 操作结果
     */
    @PostMapping("/control/{gatewaySn}/{configId}/{action}")
    public Result<?> controlChannel(
            @PathVariable String gatewaySn,
            @PathVariable Long configId,
            @PathVariable String action) {
        try {
            log.info("管控平台控制网关转发通道: 网关={}, 配置ID={}, 操作={}", gatewaySn, configId, action);
            boolean result = gatewayForwardConfigService.controlChannel(gatewaySn, configId, action);
            return result ? Result.success("操作成功") : Result.error("操作失败");
        } catch (Exception e) {
            log.error("控制通道失败", e);
            return Result.error("控制通道失败: " + e.getMessage());
        }
    }

    /**
     * 查询网关转发配置列表
     * @param gatewaySn 网关序列号
     * @return 配置列表
     */
    @GetMapping("/list/{gatewaySn}")
    public Result<?> listConfigs(@PathVariable String gatewaySn) {
        try {
            log.info("查询网关转发配置列表: 网关={}", gatewaySn);
            Object configs = gatewayForwardConfigService.getConfigsFromGateway(gatewaySn);
            return Result.data(configs);
        } catch (Exception e) {
            log.error("查询配置列表失败", e);
            return Result.error("查询配置列表失败: " + e.getMessage());
        }
    }

    /**
     * 测试网关连通性
     * @param gatewaySn 网关序列号
     * @param configId 配置ID
     * @return 测试结果
     */
    @PostMapping("/test/{gatewaySn}/{configId}")
    public Result<?> testConnection(
            @PathVariable String gatewaySn,
            @PathVariable Long configId) {
        try {
            log.info("测试网关转发通道连通性: 网关={}, 配置ID={}", gatewaySn, configId);
            boolean result = gatewayForwardConfigService.testConnectionFromGateway(gatewaySn, configId);
            return Result.data(result);
        } catch (Exception e) {
            log.error("测试连通性失败", e);
            return Result.error("测试连通性失败: " + e.getMessage());
        }
    }
}
