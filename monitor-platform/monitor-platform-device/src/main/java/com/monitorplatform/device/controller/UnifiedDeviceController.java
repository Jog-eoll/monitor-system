package com.monitorplatform.device.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitorplatform.common.entity.Result;
import com.monitorplatform.device.entity.InfoBoardModelVendorMapping;
import com.monitorplatform.device.entity.UnifiedDevice;
import com.monitorplatform.device.entity.dto.*;
import com.monitorplatform.device.service.InfoBoardModelVendorMappingService;
import com.monitorplatform.device.service.UnifiedDeviceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 统一设备管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/device/unified")
public class UnifiedDeviceController {

    @Resource
    private UnifiedDeviceService unifiedDeviceService;

    @Resource
    private InfoBoardModelVendorMappingService mappingService;

    @Value("${info-board.default-publish-gateway-url:http://127.0.0.1:8092}")
    private String defaultPublishGatewayUrl;

    @Resource
    private RestTemplate restTemplate;

    /**
     * 获取设备分类树
     * GET /device/unified/tree
     */
    @GetMapping("/tree")
    public Result<?> getDeviceTree() {
        try {
            List<DeviceTreeNodeDTO> tree = unifiedDeviceService.getDeviceTree();
            return Result.data(tree);
        } catch (Exception e) {
            log.error("获取设备树失败", e);
            return Result.error("获取设备树失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有情报板地图点位
     * GET /device/unified/map-points
     *
     * 用于在地图上以点的形式展示所有已配置的情报板位置。
     * 返回字段：id、deviceId、deviceName、longitude、latitude、status
     */
    @GetMapping("/map-points")
    public Result<?> getInfoBoardMapPoints() {
        try {
            List<Map<String, Object>> points = unifiedDeviceService.getInfoBoardMapPoints();
            return Result.data(points);
        } catch (Exception e) {
            log.error("获取情报板地图点位失败", e);
            return Result.error("获取地图点位失败: " + e.getMessage());
        }
    }

    /**
     * 统一分页查询所有设备
     * GET /device/unified/page
     * 
     * @param pageNum 页码（默认1）
     * @param pageSize 每页数量（默认10）
     * @param deviceType 设备类型（可选）: camera/gateway/server/terminal/monitor_client
     * @param status 状态（可选）: 在线/离线/告警/故障
     * @param keyword 关键词搜索（可选）
     */
    /**
     * Batch query info board devices by deviceId.
     * POST /device/unified/info-board/batch
     */
    @PostMapping("/info-board/batch")
    public Result<?> listInfoBoardsByDeviceIds(@Valid @RequestBody InfoBoardBatchQueryDTO dto) {
        try {
            List<UnifiedDeviceDTO> devices = unifiedDeviceService.listInfoBoardsByDeviceIds(dto.getDeviceIds());
            return Result.data(devices);
        } catch (Exception e) {
            log.error("batch query info boards failed", e);
            return Result.error("batch query info boards failed: " + e.getMessage());
        }
    }

    @GetMapping("/page")
    public Result<?> pageListAllDevices(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String deviceType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        try {
            Page<UnifiedDeviceDTO> page = unifiedDeviceService.pageListAllDevices(
                pageNum, pageSize, deviceType, status, keyword);
            return Result.data(page);
        } catch (Exception e) {
            log.error("查询设备列表失败", e);
            return Result.error("查询设备列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取设备详情（含监控数据）
     * GET /device/unified/{deviceType}/{deviceId}/detail
     * 
     * @param deviceType 设备类型: camera/gateway/server/terminal/monitor_client
     * @param deviceId 设备ID
     */
    @GetMapping("/{deviceType}/{deviceId}/detail")
    public Result<?> getDeviceDetail(
            @PathVariable String deviceType,
            @PathVariable String deviceId) {
        try {
            DeviceDetailDTO detail = unifiedDeviceService.getDeviceDetail(deviceType, deviceId);
            if (detail != null && detail.getDeviceId() != null) {
                return Result.data(detail);
            } else {
                return Result.error("设备不存在");
            }
        } catch (Exception e) {
            log.error("获取设备详情失败: deviceType={}, deviceId={}", deviceType, deviceId, e);
            return Result.error("获取设备详情失败: " + e.getMessage());
        }
    }

    /**
     * 批量刷新设备状态
     * POST /device/unified/batch-refresh

     */
    @PostMapping("/batch-refresh")
    public Result<?> batchRefreshStatus(@Valid @RequestBody BatchOperationDTO dto) {
        try {
            int successCount = unifiedDeviceService.batchRefreshStatus(dto);
            Map<String, Object> result = new HashMap<>();
            result.put("total", dto.getDeviceIds().size());
            result.put("successCount", successCount);
            result.put("failCount", dto.getDeviceIds().size() - successCount);
            return Result.data(result);
        } catch (Exception e) {
            log.error("批量刷新设备状态失败", e);
            return Result.error("批量刷新失败: " + e.getMessage());
        }
    }

    /**
     * 批量导出设备信息
     * POST /device/unified/batch-export
     * 
     * 请求体示例:
     * {
     *   "deviceIds": ["CAM-001", "CAM-002"],
     *   "deviceType": "camera",
     *   "operationType": "export"
     * }
     */
    @PostMapping("/batch-export")
    public void batchExport(@Valid @RequestBody BatchOperationDTO dto, HttpServletResponse response) {
        try {
            unifiedDeviceService.batchExport(dto, response);
        } catch (Exception e) {
            log.error("批量导出设备信息失败", e);
            throw new RuntimeException("批量导出失败: " + e.getMessage());
        }
    }

    /**
     * 下发设备命令
     * POST /device/unified/command
     *
     */
    @PostMapping("/command")
    public Result<?> sendCommand(@Valid @RequestBody DeviceCommandDTO dto) {
        try {
            Map<String, Object> result = unifiedDeviceService.sendCommand(dto);
            if ((boolean) result.getOrDefault("success", false)) {
                return Result.data(result);
            } else {
                return Result.error((String) result.get("message"));
            }
        } catch (Exception e) {
            log.error("下发设备命令失败", e);
            return Result.error("下发命令失败: " + e.getMessage());
        }
    }

    /**
     * 获取设备统计信息
     * GET /device/unified/statistics
     * 
     * 返回示例:
     * {
     *   "camera": {"total": 4, "online": 4, "offline": 0, "alarm": 0},
     *   "gateway": {"total": 8, "online": 8, "offline": 0},
     *   "server": {"total": 3, "online": 3},
     *   "terminal": {"total": 12, "online": 10},
     *   "totalDevices": 27,
     *   "totalOnline": 25
     * }
     */
    @GetMapping("/statistics")
    public Result<?> getDeviceStatistics() {
        try {
            Map<String, Object> statistics = unifiedDeviceService.getDeviceStatistics();
            return Result.data(statistics);
        } catch (Exception e) {
            log.error("获取设备统计信息失败", e);
            return Result.error("获取统计信息失败: " + e.getMessage());
        }
    }

    /**
     * 接收设备心跳
     * POST /device/unified/heartbeat
     */
    @PostMapping("/heartbeat")
    public Result<?> receiveHeartbeat(@Valid @RequestBody DeviceHeartbeatDTO dto) {
        try {
            boolean result = unifiedDeviceService.receiveHeartbeat(dto);
            return result ? Result.success("心跳接收成功") : Result.error("接收心跳失败");
        } catch (Exception e) {
            log.error("接收心跳异常", e);
            return Result.error("接收心跳异常: " + e.getMessage());
        }
    }

    @PutMapping("/ip")
    public Result<?> updateDeviceIp(@RequestParam String deviceId, @RequestParam String ip) {
        try {
            boolean success = unifiedDeviceService.updateDeviceIp(deviceId, ip);
            return success ? Result.success("IP更新成功") : Result.error("IP更新失败: 设备不存在");
        } catch (Exception e) {
            log.error("updateDeviceIp failed. deviceId={}, ip={}", deviceId, ip, e);
            return Result.error("更新设备IP失败: " + e.getMessage());
        }
    }

    // ========== 统一 CRUD 接口 ==========

    /**
     * 通过 IP 地址更新情报板状态（供告警服务 Feign 调用）
     * PUT /device/unified/status-by-ip?ip=xxx&status=xxx
     */
    @PutMapping("/status-by-ip")
    public Result<?> updateStatusByIp(
            @RequestParam String ip,
            @RequestParam String status) {
        try {
            boolean ok = unifiedDeviceService.updateStatusByIp(ip, status);
            return ok ? Result.success("状态更新成功") : Result.error("未找到情报板设备: ip=" + ip);
        } catch (Exception e) {
            log.error("更新情报板状态失败: ip={}, status={}", ip, status, e);
            return Result.error("更新状态失败: " + e.getMessage());
        }
    }

    /**
     * 通过 IP 查询情报板经纬度（供告警服务 Feign 调用）
     * GET /device/unified/location-by-ip?ip=xxx
     */
    /**
     * Query info board status by IP.
     * GET /device/unified/status-by-ip?ip=xxx
     */
    @GetMapping("/status-by-ip")
    public Result<?> getStatusByIp(@RequestParam String ip) {
        try {
            Map<String, Object> status = unifiedDeviceService.getInfoBoardStatusByIp(ip);
            return status.isEmpty() ? Result.error("info board not found: ip=" + ip) : Result.data(status);
        } catch (Exception e) {
            log.error("query info board status failed: ip={}", ip, e);
            return Result.error("query info board status failed: " + e.getMessage());
        }
    }

    @GetMapping("/location-by-ip")
    public Result<?> getLocationByIp(@RequestParam String ip) {
        try {
            Map<String, Object> location = unifiedDeviceService.getInfoBoardLocationByIp(ip);
            return Result.data(location);
        } catch (Exception e) {
            log.error("查询情报板经纬度失败: ip={}", ip, e);
            return Result.error("查询经纬度失败: " + e.getMessage());
        }
    }

    /**
     * 新增设备
     * POST /device/unified
     */
    @PostMapping
    public Result<?> addDevice(@Valid @RequestBody UnifiedDevice device) {
        try {
            boolean result = unifiedDeviceService.addDevice(device);
            return result ? Result.data(device) : Result.error("新增设备失败");
        } catch (Exception e) {
            log.error("新增设备异常", e);
            return Result.error("新增设备异常: " + e.getMessage());
        }
    }

    /**
     * 修改设备
     * PUT /device/unified
     */
    @PutMapping
    public Result<?> updateDevice(@Valid @RequestBody UnifiedDevice device) {
        try {
            boolean result = unifiedDeviceService.updateDevice(device);
            return result ? Result.data(device) : Result.error("更新设备失败");
        } catch (Exception e) {
            log.error("更新设备异常", e);
            return Result.error("更新设备异常: " + e.getMessage());
        }
    }

    /**
     * 删除设备（通过 deviceId）
     * DELETE /device/unified/{deviceId}
     */
    @DeleteMapping("/{deviceId}")
    public Result<?> deleteDevice(@PathVariable String deviceId) {
        try {
            boolean result = unifiedDeviceService.deleteDevice(deviceId);
            return result ? Result.success("删除成功") : Result.error("删除设备失败，设备不存在");
        } catch (Exception e) {
            log.error("删除设备异常", e);
            return Result.error("删除设备异常: " + e.getMessage());
        }
    }

    /**
     * 删除设备（通过主键 id）
     * DELETE /device/unified/id/{id}
     */
    @DeleteMapping("/id/{id}")
    public Result<?> deleteDeviceById(@PathVariable Long id) {
        try {
            boolean result = unifiedDeviceService.deleteDeviceById(id);
            return result ? Result.success("删除成功") : Result.error("删除设备失败，设备不存在");
        } catch (Exception e) {
            log.error("删除设备异常", e);
            return Result.error("删除设备异常: " + e.getMessage());
        }
    }

    /**
     * 根据ID查询设备
     * GET /device/unified/{id}
     */
    @GetMapping("/{id:\\d+}")
    public Result<?> getDeviceById(@PathVariable Long id) {
        try {
            UnifiedDevice device = unifiedDeviceService.getDeviceById(id);
            return device != null ? Result.data(device) : Result.error("设备不存在");
        } catch (Exception e) {
            log.error("查询设备异常", e);
            return Result.error("查询设备异常: " + e.getMessage());
        }
    }

    /**
     * 接收终端网关上报的情报板探测结果，批量更新设备状态
     * POST /device/unified/batch-status
     *
     * 请求体示例：
     * [
     *   { "ip": "192.168.113.239", "port": 9520, "chainId": 1, "reachable": true },
     *   { "ip": "192.168.113.240", "port": 9520, "chainId": 2, "reachable": false }
     * ]
     *
     * reachable=true  → 状态更新为"在线"（告警状态的情报板不覆盖）
     * reachable=false → 状态更新为"离线"（告警状态的情报板不覆盖）
     */
    @PostMapping("/batch-status")
    public Result<?> batchUpdateStatusByProbe(@RequestBody List<Map<String, Object>> probeResults) {
        if (probeResults == null || probeResults.isEmpty()) {
            return Result.data(null);
        }
        int successCount = 0;
        int skipCount = 0;
        for (Map<String, Object> item : probeResults) {
            String ip = (String) item.get("ip");
            Object reachableObj = item.get("reachable");
            if (ip == null || ip.isEmpty() || reachableObj == null) continue;
            boolean reachable = Boolean.TRUE.equals(reachableObj);
            String targetStatus = reachable ? "在线" : "离线";
            try {
                boolean updated = unifiedDeviceService.updateStatusByIpSkipAlarm(ip, targetStatus);
                if (updated) {
                    successCount++;
                    log.info("[BatchStatus] 更新情报板状态: ip={}, status={}", ip, targetStatus);
                } else {
                    skipCount++;
                    log.debug("[BatchStatus] 跳过（告警状态或未找到）: ip={}", ip);
                }
            } catch (Exception e) {
                log.warn("[BatchStatus] 更新失败: ip={}, error={}", ip, e.getMessage());
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("total", probeResults.size());
        data.put("updated", successCount);
        data.put("skipped", skipCount);
        log.info("[BatchStatus] 批量状态更新完成: total={}, updated={}, skipped={}",
                probeResults.size(), successCount, skipCount);
        return Result.data(data);
    }

    /**
     * 手动添加情报板 —— 根据型号映射自动填充厂商、端口，并异步注册到解密网关
     * POST /device/unified/info-board/manual
     */
    @PostMapping("/info-board/manual")
    public Result<?> addInfoBoardManual(@Valid @RequestBody InfoBoardManualAddDTO dto) {
        try {
            // Step 1: 查型号映射
            Optional<InfoBoardModelVendorMapping> mappingOpt = mappingService.findByModelCode(dto.getModelCode());
            if (!mappingOpt.isPresent()) {
                return Result.error("不支持的型号: " + dto.getModelCode() + "，请先在型号厂商映射中配置");
            }
            InfoBoardModelVendorMapping mapping = mappingOpt.get();

            // Step 2: 构建 UnifiedDevice
            UnifiedDevice device = new UnifiedDevice();
            String deviceId = dto.getDeviceId() != null && !dto.getDeviceId().trim().isEmpty()
                    ? dto.getDeviceId().trim()
                    : "IB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
            device.setDeviceId(deviceId);
            device.setDeviceName(dto.getDeviceName());
            device.setDeviceType("info_board");
            device.setIpAddress(dto.getIpAddress());
            device.setPort(dto.getPort() != null ? dto.getPort() : mapping.getDefaultPort());
            device.setManufacturer(mapping.getPlatformManufacturer());
            device.setModel(mapping.getModelCode());
            device.setLongitude(dto.getLongitude());
            device.setLatitude(dto.getLatitude());
            device.setLocation(dto.getLocation());
            device.setRegionId(dto.getRegionId());
            device.setRemark(dto.getRemark());
            device.setStatus("离线");
            device.setCreateTime(LocalDateTime.now());
            device.setUpdateTime(LocalDateTime.now());

            // Step 3: 保存设备到平台数据库
            boolean saved = unifiedDeviceService.addDevice(device);
            if (!saved) {
                return Result.error("保存设备失败");
            }

            // Step 4: 异步调用加密网关转发到解密网关注册设备（保存失败不回滚设备）
            Map<String, Object> gatewayResult = new LinkedHashMap<>();
            gatewayResult.put("status", "FAILED");
            gatewayResult.put("lastAttemptTime", LocalDateTime.now().toString());

            String publishGatewayUrl = dto.getPublishGatewayUrl() != null && !dto.getPublishGatewayUrl().trim().isEmpty()
                    ? dto.getPublishGatewayUrl().trim() : defaultPublishGatewayUrl;

            try {
                Map<String, Object> registerBody = new LinkedHashMap<>();
                registerBody.put("deviceId", deviceId);
                registerBody.put("ip", dto.getIpAddress());
                registerBody.put("port", device.getPort());
                registerBody.put("vendor", mapping.getTerminalVendorHint());
                registerBody.put("terminalGatewayUrl", dto.getTerminalGatewayUrl());
                registerBody.put("sn", dto.getDeviceId());

                String url = publishGatewayUrl + "/api/terminal-devices/register";
                @SuppressWarnings("unchecked")
                Map<String, Object> resp = restTemplate.postForObject(url, registerBody, Map.class);

                if (resp != null && Integer.valueOf(200).equals(resp.get("code"))) {
                    gatewayResult.put("status", "SUCCESS");
                    gatewayResult.put("message", "解密网关注册成功");
                    log.info("[InfoBoard手动添加] 解密网关注册成功: deviceId={}, gatewayResult={}", deviceId, resp);
                } else {
                    gatewayResult.put("status", "FAILED");
                    gatewayResult.put("message", resp != null ? resp.get("msg") : "加密网关返回空");
                    log.warn("[InfoBoard手动添加] 解密网关注册失败: deviceId={}, resp={}", deviceId, resp);
                }
            } catch (Exception e) {
                gatewayResult.put("status", "FAILED");
                gatewayResult.put("message", "网关调用异常: " + e.getMessage());
                log.error("[InfoBoard手动添加] 调用加密网关异常: deviceId={}", deviceId, e);
            }

            gatewayResult.put("targetGateway", publishGatewayUrl);
            gatewayResult.put("terminalGatewayUrl", dto.getTerminalGatewayUrl());
            device.setExtraInfo(buildExtraInfo(device.getExtraInfo(), "gatewayRegister", gatewayResult));
            unifiedDeviceService.updateExtraInfo(deviceId, device.getExtraInfo());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("device", device);
            result.put("gatewayRegister", gatewayResult);
            return Result.data(result);
        } catch (Exception e) {
            log.error("手动添加情报板异常", e);
            return Result.error("手动添加情报板失败: " + e.getMessage());
        }
    }

    /**
     * 重试情报板网关注册
     * POST /device/unified/info-board/{deviceId}/gateway-register
     */
    @PostMapping("/info-board/{deviceId}/gateway-register")
    public Result<?> retryGatewayRegister(@PathVariable String deviceId) {
        try {
            UnifiedDevice device = unifiedDeviceService.findByDeviceId(deviceId);
            if (device == null || !"info_board".equals(device.getDeviceType())) {
                return Result.error("情报板设备不存在: " + deviceId);
            }

            InfoBoardModelVendorMapping mapping = null;
            if (device.getModel() != null) {
                mapping = mappingService.findByModelCode(device.getModel()).orElse(null);
            }

            String vendorHint = mapping != null ? mapping.getTerminalVendorHint() : "COLORLIGHT";

            // 从上次 extraInfo 恢复 terminalGatewayUrl，避免回退到加密网关默认值
            String terminalGatewayUrl = readGatewayRegisterField(device.getExtraInfo(), "terminalGatewayUrl");

            Map<String, Object> registerBody = new LinkedHashMap<>();
            registerBody.put("deviceId", deviceId);
            registerBody.put("ip", device.getIpAddress());
            registerBody.put("port", device.getPort());
            registerBody.put("vendor", vendorHint);
            registerBody.put("sn", device.getDeviceId());
            if (terminalGatewayUrl != null && !terminalGatewayUrl.isEmpty()) {
                registerBody.put("terminalGatewayUrl", terminalGatewayUrl);
            }

            Map<String, Object> gatewayResult = new LinkedHashMap<>();
            gatewayResult.put("lastAttemptTime", LocalDateTime.now().toString());

            try {
                String url = defaultPublishGatewayUrl + "/api/terminal-devices/register";
                @SuppressWarnings("unchecked")
                Map<String, Object> resp = restTemplate.postForObject(url, registerBody, Map.class);

                if (resp != null && Integer.valueOf(200).equals(resp.get("code"))) {
                    gatewayResult.put("status", "SUCCESS");
                    gatewayResult.put("message", "解密网关注册成功");
                } else {
                    gatewayResult.put("status", "FAILED");
                    gatewayResult.put("message", resp != null ? resp.get("msg") : "加密网关返回空");
                }
            } catch (Exception e) {
                gatewayResult.put("status", "FAILED");
                gatewayResult.put("message", "网关调用异常: " + e.getMessage());
                log.error("[InfoBoard重试] 调用加密网关异常: deviceId={}", deviceId, e);
            }

            gatewayResult.put("targetGateway", defaultPublishGatewayUrl);
            if (terminalGatewayUrl != null && !terminalGatewayUrl.isEmpty()) {
                gatewayResult.put("terminalGatewayUrl", terminalGatewayUrl);
            }
            String extraInfo = buildExtraInfo(device.getExtraInfo(), "gatewayRegister", gatewayResult);
            unifiedDeviceService.updateExtraInfo(deviceId, extraInfo);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("deviceId", deviceId);
            result.put("gatewayRegister", gatewayResult);
            return "SUCCESS".equals(gatewayResult.get("status"))
                    ? Result.success("重试注册成功", result)
                    : Result.data(result);
        } catch (Exception e) {
            log.error("重试网关注册异常: deviceId={}", deviceId, e);
            return Result.error("重试注册失败: " + e.getMessage());
        }
    }

    /**
     * 从 extraInfo JSON 中读取 gatewayRegister 子字段（用于重试时恢复上次参数）。
     */
    private String readGatewayRegisterField(String extraInfoJson, String field) {
        try {
            if (extraInfoJson == null || extraInfoJson.trim().isEmpty()) {
                return null;
            }
            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> map = mapper.readValue(extraInfoJson, Map.class);
            Object gatewayRegister = map.get("gatewayRegister");
            if (gatewayRegister instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> regMap = (Map<String, Object>) gatewayRegister;
                Object value = regMap.get(field);
                return value != null ? value.toString() : null;
            }
        } catch (Exception e) {
            log.warn("[readGatewayRegisterField] 读取 extraInfo 失败: field={}", field, e);
        }
        return null;
    }

    /**
     * 向 extraInfo JSON 中合并新字段
     */
    @SuppressWarnings("unchecked")
    private String buildExtraInfo(String existingJson, String key, Object value) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> map;
            if (existingJson != null && !existingJson.trim().isEmpty()) {
                map = mapper.readValue(existingJson, Map.class);
            } else {
                map = new LinkedHashMap<>();
            }
            map.put(key, value);
            return mapper.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("[buildExtraInfo] JSON处理失败: key={}", key, e);
            return "{\"" + key + "\":{}}";
        }
    }

    /**
     * 更新设备经纬度（地图拖拽定位保存）
     * POST /device/unified/updateLocation
     *
     * 请求体示例:
     * { "deviceId": "info_board2", "longitude": 113.2644, "latitude": 23.1291 }
     */
    @PostMapping("/updateLocation")
    public Result<?> updateLocation(@RequestBody Map<String, Object> body) {
        try {
            Object deviceIdObj = body.get("deviceId");
            Object longitudeObj = body.get("longitude");
            Object latitudeObj = body.get("latitude");

            if (deviceIdObj == null || longitudeObj == null || latitudeObj == null) {
                return Result.error("参数缺失: deviceId、longitude、latitude 均不能为空");
            }

            String deviceId = deviceIdObj.toString();
            Double longitude = Double.valueOf(longitudeObj.toString());
            Double latitude = Double.valueOf(latitudeObj.toString());

            boolean result = unifiedDeviceService.updateDeviceLocation(deviceId, longitude, latitude);
            return result ? Result.success("更新成功") : Result.error("更新经纬度失败: 设备不存在或非情报板类型");
        } catch (NumberFormatException e) {
            log.error("更新经纬度参数格式错误: {}", body, e);
            return Result.error("参数格式错误: longitude、latitude 必须为数字");
        } catch (Exception e) {
            log.error("更新经纬度异常", e);
            return Result.error("更新经纬度失败: " + e.getMessage());
        }
    }
}
