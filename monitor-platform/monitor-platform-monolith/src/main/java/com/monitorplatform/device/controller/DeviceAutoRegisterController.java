package com.monitorplatform.device.controller;

import com.monitorplatform.common.entity.Result;
import com.monitorplatform.device.entity.UnifiedDevice;
import com.monitorplatform.device.entity.dto.DeviceRegisterDTO;
import com.monitorplatform.device.service.DeviceAutoRegisterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 设备自动注册控制器
 */
@Slf4j
@RestController
@RequestMapping("/device/registry")
public class DeviceAutoRegisterController {

    @Resource
    private DeviceAutoRegisterService deviceAutoRegisterService;

    /**
     * 设备自动注册（新增或更新）
     * POST /device/registry/auto-register
     */
    @PostMapping("/auto-register")
    public Result<Void> register(@Valid @RequestBody DeviceRegisterDTO dto) {
        boolean success = deviceAutoRegisterService.register(dto);
        if (success) {
            return Result.success();
        } else {
            return Result.error("设备注册失败");
        }
    }

    /**
     * 心跳上报
     * POST /device/registry/heartbeat/{instanceId}
     */
    @PostMapping("/heartbeat/{instanceId}")
    public Result<Void> heartbeat(@PathVariable String instanceId) {
        boolean success = deviceAutoRegisterService.heartbeat(instanceId);
        if (success) {
            return Result.success();
        } else {
            return Result.error("心跳处理失败");
        }
    }

    /**
     * 设备注销
     * DELETE /device/registry/deregister/{instanceId}
     */
    @DeleteMapping("/deregister/{instanceId}")
    public Result<Void> deregister(@PathVariable String instanceId) {
        boolean success = deviceAutoRegisterService.deregister(instanceId);
        if (success) {
            return Result.success();
        } else {
            return Result.error("设备注销失败");
        }
    }

    // ==================== 设备发现与管理（集成自 registry-server） ====================

    /**
     * 按设备类型查询在线设备
     * GET /device/registry/discover/{deviceType}
     */
    @GetMapping("/discover/{deviceType}")
    public Result<List<UnifiedDevice>> discoverDevices(@PathVariable String deviceType) {
        List<UnifiedDevice> devices = deviceAutoRegisterService.discoverByDeviceType(deviceType);
        if (devices != null) {
            return Result.success(devices);
        } else {
            return Result.error("设备发现失败");
        }
    }

    /**
     * 按 instanceId 查询单个设备
     * GET /device/registry/instance/{instanceId}
     */
    @GetMapping("/instance/{instanceId}")
    public Result<UnifiedDevice> getDeviceInstance(@PathVariable String instanceId) {
        UnifiedDevice device = deviceAutoRegisterService.getDeviceByInstanceId(instanceId);
        if (device != null) {
            return Result.success(device);
        } else {
            return Result.error("设备实例不存在");
        }
    }

    /**
     * 获取所有设备列表
     * GET /device/registry/services
     */
    @GetMapping("/services")
    public Result<List<UnifiedDevice>> getAllDevices() {
        List<UnifiedDevice> devices = deviceAutoRegisterService.getAllRegisteredDevices();
        if (devices != null) {
            return Result.success(devices);
        } else {
            return Result.error("获取设备列表失败");
        }
    }

    /**
     * 获取所有在线设备类型列表
     * GET /device/registry/service-names
     */
    @GetMapping("/service-names")
    public Result<List<String>> getAllDeviceTypes() {
        List<String> deviceTypes = deviceAutoRegisterService.getAllDeviceTypes();
        if (deviceTypes != null) {
            return Result.success(deviceTypes);
        } else {
            return Result.error("获取设备类型列表失败");
        }
    }

    /**
     * 手动更新设备状态
     * PUT /device/registry/status/{instanceId}/{status}
     */
    @PutMapping("/status/{instanceId}/{status}")
    public Result<Void> updateDeviceStatus(@PathVariable String instanceId,
                                                   @PathVariable String status) {
        boolean success = deviceAutoRegisterService.updateDeviceStatus(instanceId, status);
        if (success) {
            return Result.success();
        } else {
            return Result.error("更新设备状态失败");
        }
    }

    /**
     * 获取设备统计信息
     * GET /device/registry/statistics
     */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> getDeviceStatistics() {
        Map<String, Object> statistics = deviceAutoRegisterService.getDeviceStatistics();
        if (statistics != null) {
            return Result.success(statistics);
        } else {
            return Result.error("获取设备统计失败");
        }
    }

    /**
     * 按设备类型批量注销
     * DELETE /device/registry/services/{deviceType}
     */
    @DeleteMapping("/services/{deviceType}")
    public Result<Integer> batchDeregisterByDeviceType(@PathVariable String deviceType) {
        int count = deviceAutoRegisterService.batchDeregisterByDeviceType(deviceType);
        log.info("批量注销设备: deviceType={}, count={}", deviceType, count);
        return Result.success(count);
    }
}
