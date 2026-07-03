package com.monitorplatform.device.service;

import com.monitorplatform.device.entity.UnifiedDevice;
import com.monitorplatform.device.entity.dto.DeviceRegisterDTO;

import java.util.List;
import java.util.Map;

/**
 * 设备自动注册服务
 * 接收 registry-client 上报的注册/心跳/注销请求，直接映射到 unified_device_info 表
 * 同时提供设备发现、查询、统计等管理能力（原 registry-server 功能）
 */
public interface DeviceAutoRegisterService {

    /**
     * 注册设备（新增或更新）
     * 按 instanceId -> deviceId 查找：存在则更新，不存在则新增
     *
     * @param dto 注册请求
     * @return 是否成功
     */
    boolean register(DeviceRegisterDTO dto);

    /**
     * 心跳上报
     * 更新 lastOnlineTime、status="在线"，同时写 Redis 心跳 key
     *
     * @param instanceId 实例ID（对应 deviceId）
     * @return 是否成功
     */
    boolean heartbeat(String instanceId);

    /**
     * 注销设备
     * 将设备 status 更新为 "离线"
     *
     * @param instanceId 实例ID（对应 deviceId）
     * @return 是否成功
     */
    boolean deregister(String instanceId);

    // ==================== 设备发现与管理（集成自 registry-server） ====================

    /**
     * 按设备类型查询在线设备列表
     * 对应 registry-server 的 discoverServices(serviceName)
     *
     * @param deviceType 设备类型（如 publish_gateway, terminal_encrypt_gateway）
     * @return 在线设备列表
     */
    List<UnifiedDevice> discoverByDeviceType(String deviceType);

    /**
     * 按 instanceId（deviceId）查询单个设备
     * 对应 registry-server 的 getServiceInstance(instanceId)
     *
     * @param instanceId 实例ID（对应 deviceId）
     * @return 设备信息，不存在返回 null
     */
    UnifiedDevice getDeviceByInstanceId(String instanceId);

    /**
     * 获取所有自动注册的设备列表
     * 对应 registry-server 的 getAllServices()
     *
     * @return 所有设备列表
     */
    List<UnifiedDevice> getAllRegisteredDevices();

    /**
     * 获取所有在线设备的设备类型列表（去重）
     * 对应 registry-server 的 getAllServiceNames()
     *
     * @return 设备类型列表
     */
    List<String> getAllDeviceTypes();

    /**
     * 手动更新设备状态
     * 对应 registry-server 的 updateServiceStatus(instanceId, status)
     *
     * @param instanceId 实例ID（对应 deviceId）
     * @param status 目标状态（在线/离线/告警）
     * @return 是否成功
     */
    boolean updateDeviceStatus(String instanceId, String status);

    /**
     * 获取设备统计信息（总数/在线/离线/告警）
     * 对应 registry-server 的 getServiceStatistics()
     *
     * @return 统计信息 Map
     */
    Map<String, Object> getDeviceStatistics();

    /**
     * 按设备类型批量注销（标记离线）
     * 对应 registry-server 的 batchDeregisterByServiceName(serviceName)
     *
     * @param deviceType 设备类型
     * @return 注销的设备数量
     */
    int batchDeregisterByDeviceType(String deviceType);
}
