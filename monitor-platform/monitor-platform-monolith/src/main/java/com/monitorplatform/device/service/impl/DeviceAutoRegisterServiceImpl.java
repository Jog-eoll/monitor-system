package com.monitorplatform.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.monitorplatform.device.entity.UnifiedDevice;
import com.monitorplatform.device.entity.dto.DeviceRegisterDTO;
import com.monitorplatform.device.mapper.UnifiedDeviceMapper;
import com.monitorplatform.device.service.DeviceAutoRegisterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 设备自动注册服务实现
 * 将 registry-client 的上报请求直接映射到 unified_device_info 表
 * 同时提供设备发现、查询、统计等管理能力（集成自 registry-server）
 */
@Slf4j
@Service
public class DeviceAutoRegisterServiceImpl implements DeviceAutoRegisterService {

    @Resource
    private UnifiedDeviceMapper unifiedDeviceMapper;

    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate stringRedisTemplate;

    private static final String HEARTBEAT_KEY_PREFIX = "device:heartbeat:";
    private static final String STATUS_KEY_PREFIX = "device:status:";

    @Override
    public boolean register(DeviceRegisterDTO dto) {
        try {
            LocalDateTime now = LocalDateTime.now();

            // 按 instanceId -> deviceId 查找现有设备
            LambdaQueryWrapper<UnifiedDevice> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UnifiedDevice::getDeviceId, dto.getInstanceId());
            UnifiedDevice existing = unifiedDeviceMapper.selectOne(wrapper);

            if (existing != null) {
                // 设备已存在：更新 IP、端口、MAC、状态 + 新增字段
                existing.setIpAddress(dto.getHost());
                existing.setPort(dto.getPort());
                existing.setMac(dto.getMacAddress());
                existing.setStatus("在线");
                existing.setLastOnlineTime(now);
                existing.setUpdateTime(now);
                // 新增字段：仅在客户端上报了非空值时才更新（避免覆盖手动配置）
                if (dto.getLocation() != null && !dto.getLocation().isEmpty()) {
                    existing.setLocation(dto.getLocation());
                }
                if (dto.getVersion() != null && !dto.getVersion().isEmpty()) {
                    existing.setVersion(dto.getVersion());
                }
                if (dto.getManufacturer() != null && !dto.getManufacturer().isEmpty()) {
                    existing.setManufacturer(dto.getManufacturer());
                }
                if (dto.getModel() != null && !dto.getModel().isEmpty()) {
                    existing.setModel(dto.getModel());
                }
                if (dto.getRemark() != null && !dto.getRemark().isEmpty()) {
                    existing.setRemark(dto.getRemark());
                }
                unifiedDeviceMapper.updateById(existing);
                log.info("设备自动注册（更新）: deviceId={}, ip={}, port={}",
                        dto.getInstanceId(), dto.getHost(), dto.getPort());
            } else {
                // 新设备：创建记录
                UnifiedDevice device = new UnifiedDevice();
                device.setDeviceId(dto.getInstanceId());
                device.setDeviceName(generateDeviceName(dto));
                device.setDeviceType(dto.getDeviceType());
                device.setIpAddress(dto.getHost());
                device.setPort(dto.getPort());
                device.setMac(dto.getMacAddress());
                device.setStatus("在线");
                device.setLastOnlineTime(now);
                device.setCreateTime(now);
                device.setUpdateTime(now);
                // 新增字段
                device.setLocation(dto.getLocation());
                device.setVersion(dto.getVersion());
                device.setManufacturer(dto.getManufacturer());
                device.setModel(dto.getModel());
                device.setRemark(dto.getRemark());
                unifiedDeviceMapper.insert(device);
                log.info("设备自动注册（新增）: deviceId={}, deviceType={}, ip={}:{}",
                        dto.getInstanceId(), dto.getDeviceType(), dto.getHost(), dto.getPort());
            }

            // 写 Redis 心跳 key
            writeHeartbeatToRedis(dto.getInstanceId());
            return true;
        } catch (Exception e) {
            log.error("设备自动注册失败: instanceId={}", dto.getInstanceId(), e);
            return false;
        }
    }

    @Override
    public boolean heartbeat(String instanceId) {
        try {
            LambdaQueryWrapper<UnifiedDevice> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UnifiedDevice::getDeviceId, instanceId);
            UnifiedDevice device = unifiedDeviceMapper.selectOne(wrapper);

            if (device == null) {
                log.warn("收到未注册设备的心跳: {}", instanceId);
                return false;
            }

            LocalDateTime now = LocalDateTime.now();
            device.setLastOnlineTime(now);
            // 仅在非告警状态时更新为在线（告警状态由告警服务管理）
            if (!"告警".equals(device.getStatus())) {
                device.setStatus("在线");
            }
            device.setUpdateTime(now);
            unifiedDeviceMapper.updateById(device);

            // 写 Redis 心跳 key
            writeHeartbeatToRedis(instanceId);

            log.debug("设备心跳: deviceId={}", instanceId);
            return true;
        } catch (Exception e) {
            log.error("设备心跳处理失败: instanceId={}", instanceId, e);
            return false;
        }
    }

    @Override
    public boolean deregister(String instanceId) {
        try {
            LambdaQueryWrapper<UnifiedDevice> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UnifiedDevice::getDeviceId, instanceId);
            UnifiedDevice device = unifiedDeviceMapper.selectOne(wrapper);

            if (device == null) {
                log.warn("注销未知设备: {}", instanceId);
                return false;
            }

            device.setStatus("离线");
            device.setUpdateTime(LocalDateTime.now());
            unifiedDeviceMapper.updateById(device);

            // 清除 Redis 心跳 key
            stringRedisTemplate.delete(HEARTBEAT_KEY_PREFIX + instanceId);
            stringRedisTemplate.opsForValue().set(
                    STATUS_KEY_PREFIX + instanceId, "离线", 5, TimeUnit.MINUTES);

            log.info("设备注销: deviceId={}", instanceId);
            return true;
        } catch (Exception e) {
            log.error("设备注销失败: instanceId={}", instanceId, e);
            return false;
        }
    }

    /**
     * 写 Redis 心跳 key，TTL 70秒（大于心跳间隔 10秒 + 健康检查间隔 60秒）
     */
    private void writeHeartbeatToRedis(String deviceId) {
        stringRedisTemplate.opsForValue().set(
                HEARTBEAT_KEY_PREFIX + deviceId,
                String.valueOf(System.currentTimeMillis()),
                70, TimeUnit.SECONDS);
        stringRedisTemplate.opsForValue().set(
                STATUS_KEY_PREFIX + deviceId,
                "在线", 5, TimeUnit.MINUTES);
    }

    // ==================== 设备发现与管理（集成自 registry-server） ====================

    @Override
    public List<UnifiedDevice> discoverByDeviceType(String deviceType) {
        try {
            LambdaQueryWrapper<UnifiedDevice> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UnifiedDevice::getDeviceType, deviceType)
                   .eq(UnifiedDevice::getStatus, "在线");
            return unifiedDeviceMapper.selectList(wrapper);
        } catch (Exception e) {
            log.error("按设备类型查询在线设备失败: {}", deviceType, e);
            return null;
        }
    }

    @Override
    public UnifiedDevice getDeviceByInstanceId(String instanceId) {
        try {
            LambdaQueryWrapper<UnifiedDevice> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UnifiedDevice::getDeviceId, instanceId);
            return unifiedDeviceMapper.selectOne(wrapper);
        } catch (Exception e) {
            log.error("查询设备失败: {}", instanceId, e);
            return null;
        }
    }

    @Override
    public List<UnifiedDevice> getAllRegisteredDevices() {
        try {
            return unifiedDeviceMapper.selectList(null);
        } catch (Exception e) {
            log.error("获取所有设备列表失败", e);
            return null;
        }
    }

    @Override
    public List<String> getAllDeviceTypes() {
        try {
            LambdaQueryWrapper<UnifiedDevice> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UnifiedDevice::getStatus, "在线");
            List<UnifiedDevice> devices = unifiedDeviceMapper.selectList(wrapper);
            return devices.stream()
                    .map(UnifiedDevice::getDeviceType)
                    .filter(type -> type != null && !type.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("获取设备类型列表失败", e);
            return null;
        }
    }

    @Override
    public boolean updateDeviceStatus(String instanceId, String status) {
        try {
            LambdaQueryWrapper<UnifiedDevice> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UnifiedDevice::getDeviceId, instanceId);
            UnifiedDevice device = unifiedDeviceMapper.selectOne(wrapper);

            if (device != null) {
                device.setStatus(status);
                device.setUpdateTime(LocalDateTime.now());
                unifiedDeviceMapper.updateById(device);
                log.info("更新设备状态: deviceId={}, status={}", instanceId, status);
                return true;
            }
            log.warn("更新状态失败，设备不存在: {}", instanceId);
            return false;
        } catch (Exception e) {
            log.error("更新设备状态失败: {}", instanceId, e);
            return false;
        }
    }

    @Override
    public Map<String, Object> getDeviceStatistics() {
        try {
            Map<String, Object> stats = new HashMap<>();

            long total = unifiedDeviceMapper.selectCount(null);
            stats.put("total", total);

            LambdaQueryWrapper<UnifiedDevice> onlineWrapper = new LambdaQueryWrapper<>();
            onlineWrapper.eq(UnifiedDevice::getStatus, "在线");
            long onlineCount = unifiedDeviceMapper.selectCount(onlineWrapper);
            stats.put("online", onlineCount);

            LambdaQueryWrapper<UnifiedDevice> offlineWrapper = new LambdaQueryWrapper<>();
            offlineWrapper.eq(UnifiedDevice::getStatus, "离线");
            long offlineCount = unifiedDeviceMapper.selectCount(offlineWrapper);
            stats.put("offline", offlineCount);

            LambdaQueryWrapper<UnifiedDevice> alarmWrapper = new LambdaQueryWrapper<>();
            alarmWrapper.eq(UnifiedDevice::getStatus, "告警");
            long alarmCount = unifiedDeviceMapper.selectCount(alarmWrapper);
            stats.put("alarm", alarmCount);

            List<String> deviceTypes = getAllDeviceTypes();
            stats.put("deviceTypeCount", deviceTypes != null ? deviceTypes.size() : 0);
            stats.put("deviceTypes", deviceTypes);

            return stats;
        } catch (Exception e) {
            log.error("获取设备统计信息失败", e);
            return null;
        }
    }

    @Override
    public int batchDeregisterByDeviceType(String deviceType) {
        try {
            LambdaQueryWrapper<UnifiedDevice> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UnifiedDevice::getDeviceType, deviceType);
            List<UnifiedDevice> devices = unifiedDeviceMapper.selectList(wrapper);

            int count = 0;
            LocalDateTime now = LocalDateTime.now();
            for (UnifiedDevice device : devices) {
                device.setStatus("离线");
                device.setUpdateTime(now);
                unifiedDeviceMapper.updateById(device);
                // 清除 Redis 心跳 key
                stringRedisTemplate.delete(HEARTBEAT_KEY_PREFIX + device.getDeviceId());
                stringRedisTemplate.opsForValue().set(
                        STATUS_KEY_PREFIX + device.getDeviceId(), "离线", 5, TimeUnit.MINUTES);
                count++;
            }

            if (count > 0) {
                log.info("批量注销设备: deviceType={}, count={}", deviceType, count);
            }
            return count;
        } catch (Exception e) {
            log.error("批量注销设备失败: deviceType={}", deviceType, e);
            return 0;
        }
    }

    // ==================== 私有工具方法 ====================

    /**
     * 根据设备类型生成可读的设备名称
     */
    private String generateDeviceName(DeviceRegisterDTO dto) {
        String typeLabel;
        if (dto.getDeviceType() == null) {
            typeLabel = dto.getServiceName();
        } else {
            switch (dto.getDeviceType()) {
                case "publish_server":
                    typeLabel = "信息发布服务器";
                    break;
                case "publish_gateway":
                    typeLabel = "发布端加密网关";
                    break;
                case "terminal_encrypt_gateway":
                    typeLabel = "终端加密网关";
                    break;
                case "content_server":
                    typeLabel = "内容识别服务器";
                    break;
                default:
                    typeLabel = dto.getServiceName();
                    break;
            }
        }
        return typeLabel + "(" + dto.getHost() + ")";
    }
}
