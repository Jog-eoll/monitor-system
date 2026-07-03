package com.monitorplatform.registry.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.monitorplatform.registry.dto.ServiceRegisterDTO;
import com.monitorplatform.registry.entity.ServiceInstance;
import com.monitorplatform.registry.mapper.ServiceInstanceMapper;
import com.monitorplatform.registry.service.ClientConfigService;
import com.monitorplatform.registry.service.ServiceRegistryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ServiceRegistryServiceImpl implements ServiceRegistryService {

    private static final String HEARTBEAT_KEY_PREFIX = "service:heartbeat:";
    private static final int HEARTBEAT_TIMEOUT = 30;
    private static final String STATUS_ONLINE = "在线";
    private static final String STATUS_OFFLINE = "离线";

    @Autowired
    private ServiceInstanceMapper serviceInstanceMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ClientConfigService clientConfigService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean registerService(ServiceRegisterDTO dto) {
        if (dto == null) {
            log.warn("register service failed: dto is null");
            return false;
        }
        try {
            normalizeRegisterDto(dto);
            ServiceInstance existing = findExistingInstance(dto);

            LocalDateTime now = LocalDateTime.now();
            ServiceInstance instance = existing == null ? new ServiceInstance() : existing;
            fillInstance(instance, dto, now, existing == null);

            if (existing != null) {
                serviceInstanceMapper.updateById(instance);
                log.info("updated device registry: deviceId={}, clientId={}",
                        instance.getDeviceId(), dto.getClientId());
            } else {
                serviceInstanceMapper.insert(instance);
                log.info("registered device: deviceId={}, clientId={}",
                        instance.getDeviceId(), dto.getClientId());
            }

            try {
                clientConfigService.ensureDefaultConfig(instance);
            } catch (Exception e) {
                log.warn("skip client config init after device register: deviceId={}, error={}",
                        instance.getDeviceId(), e.getMessage());
            }
            return true;
        } catch (Exception e) {
            log.error("register service failed: instanceId={}, clientId={}",
                    dto.getInstanceId(), dto.getClientId(), e);
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deregisterService(String instanceId) {
        try {
            LambdaQueryWrapper<ServiceInstance> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ServiceInstance::getDeviceId, instanceId);
            ServiceInstance instance = serviceInstanceMapper.selectOne(wrapper);

            if (instance != null) {
                instance.setStatus(STATUS_OFFLINE);
                instance.setUpdateTime(LocalDateTime.now());
                serviceInstanceMapper.updateById(instance);
                stringRedisTemplate.delete(HEARTBEAT_KEY_PREFIX + instanceId);
                log.info("deregistered service instance: {}", instanceId);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("deregister service failed: {}", instanceId, e);
            return false;
        }
    }

    @Override
    public boolean heartbeat(String instanceId) {
        try {
            LambdaQueryWrapper<ServiceInstance> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ServiceInstance::getDeviceId, instanceId);
            ServiceInstance instance = serviceInstanceMapper.selectOne(wrapper);

            if (instance != null) {
                LocalDateTime now = LocalDateTime.now();
                instance.setLastOnlineTime(now);
                if (!"告警".equals(instance.getStatus())) {
                    instance.setStatus(STATUS_ONLINE);
                }
                instance.setUpdateTime(now);
                serviceInstanceMapper.updateById(instance);

                stringRedisTemplate.opsForValue().set(
                        HEARTBEAT_KEY_PREFIX + instanceId,
                        String.valueOf(System.currentTimeMillis()),
                        HEARTBEAT_TIMEOUT,
                        TimeUnit.SECONDS
                );
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("heartbeat failed: {}", instanceId, e);
            return false;
        }
    }

    @Override
    public List<ServiceInstance> discoverServices(String serviceName) {
        try {
            LambdaQueryWrapper<ServiceInstance> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ServiceInstance::getDeviceType, serviceName)
                    .eq(ServiceInstance::getStatus, STATUS_ONLINE);
            return serviceInstanceMapper.selectList(wrapper);
        } catch (Exception e) {
            log.error("discover services failed: {}", serviceName, e);
            return Collections.emptyList();
        }
    }

    @Override
    public ServiceInstance getServiceInstance(String instanceId) {
        try {
            LambdaQueryWrapper<ServiceInstance> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ServiceInstance::getDeviceId, instanceId);
            return serviceInstanceMapper.selectOne(wrapper);
        } catch (Exception e) {
            log.error("get service instance failed: {}", instanceId, e);
            return null;
        }
    }

    @Override
    public ServiceInstance getServiceInstanceByClientId(String clientId) {
        try {
            if (clientId == null || clientId.trim().isEmpty()) {
                return null;
            }
            LambdaQueryWrapper<ServiceInstance> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ServiceInstance::getDeviceId, clientId.trim());
            return serviceInstanceMapper.selectOne(wrapper);
        } catch (Exception e) {
            log.error("get service instance by clientId failed: {}", clientId, e);
            return null;
        }
    }

    @Override
    public List<ServiceInstance> getAllServices() {
        try {
            return serviceInstanceMapper.selectList(null);
        } catch (Exception e) {
            log.error("get all services failed", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<String> getAllServiceNames() {
        try {
            LambdaQueryWrapper<ServiceInstance> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ServiceInstance::getStatus, STATUS_ONLINE);
            List<ServiceInstance> instances = serviceInstanceMapper.selectList(wrapper);
            return instances.stream()
                    .map(ServiceInstance::getDeviceType)
                    .filter(name -> name != null && !name.trim().isEmpty())
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("get all service names failed", e);
            return Collections.emptyList();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateServiceStatus(String instanceId, String status) {
        try {
            LambdaQueryWrapper<ServiceInstance> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ServiceInstance::getDeviceId, instanceId);
            ServiceInstance instance = serviceInstanceMapper.selectOne(wrapper);

            if (instance != null) {
                instance.setStatus(normalizeStatus(status));
                instance.setUpdateTime(LocalDateTime.now());
                serviceInstanceMapper.updateById(instance);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("update service status failed: {}", instanceId, e);
            return false;
        }
    }

    @Override
    public Map<String, Object> getServiceStatistics() {
        try {
            Map<String, Object> stats = new HashMap<>();

            long total = serviceInstanceMapper.selectCount(null);
            stats.put("total", total);

            LambdaQueryWrapper<ServiceInstance> upWrapper = new LambdaQueryWrapper<>();
            upWrapper.eq(ServiceInstance::getStatus, STATUS_ONLINE);
            long upCount = serviceInstanceMapper.selectCount(upWrapper);
            stats.put("up", upCount);
            stats.put("online", upCount);

            LambdaQueryWrapper<ServiceInstance> downWrapper = new LambdaQueryWrapper<>();
            downWrapper.eq(ServiceInstance::getStatus, STATUS_OFFLINE);
            long downCount = serviceInstanceMapper.selectCount(downWrapper);
            stats.put("down", downCount);
            stats.put("offline", downCount);

            List<String> serviceNames = getAllServiceNames();
            stats.put("serviceCount", serviceNames.size());
            stats.put("services", serviceNames);

            return stats;
        } catch (Exception e) {
            log.error("get service statistics failed", e);
            return Collections.emptyMap();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchDeregisterByServiceName(String serviceName) {
        try {
            LambdaQueryWrapper<ServiceInstance> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ServiceInstance::getDeviceType, serviceName);
            List<ServiceInstance> instances = serviceInstanceMapper.selectList(wrapper);
            int count = 0;
            LocalDateTime now = LocalDateTime.now();
            for (ServiceInstance instance : instances) {
                instance.setStatus(STATUS_OFFLINE);
                instance.setUpdateTime(now);
                serviceInstanceMapper.updateById(instance);
                if (instance.getDeviceId() != null) {
                    stringRedisTemplate.delete(HEARTBEAT_KEY_PREFIX + instance.getDeviceId());
                }
                count++;
            }

            if (count > 0) {
                log.info("batch deregistered service instances: serviceName={}, count={}", serviceName, count);
            }

            return count;
        } catch (Exception e) {
            log.error("batch deregister by service name failed: {}", serviceName, e);
            return 0;
        }
    }

    private void normalizeRegisterDto(ServiceRegisterDTO dto) {
        if (dto.getClientId() != null) {
            dto.setClientId(dto.getClientId().trim());
        }
        if (dto.getWeight() == null) {
            dto.setWeight(1);
        }
        if (dto.getClusterName() == null || dto.getClusterName().trim().isEmpty()) {
            dto.setClusterName("default");
        }
    }

    private void fillInstance(ServiceInstance instance, ServiceRegisterDTO dto,
                              LocalDateTime now, boolean create) {
        String deviceId = resolveDeviceId(dto);
        instance.setDeviceId(deviceId);
        instance.setDeviceName(buildDeviceName(dto));
        instance.setDeviceType(resolveDeviceType(dto));
        instance.setIpAddress(dto.getHost());
        instance.setPort(dto.getPort());
        instance.setMac(dto.getMacAddress());
        instance.setStatus(STATUS_ONLINE);
        instance.setLastOnlineTime(now);
        instance.setUpdateTime(now);
        if (create) {
            instance.setCreateTime(now);
        }
    }

    private String resolveDeviceId(ServiceRegisterDTO dto) {
        if (dto.getClientId() != null && !dto.getClientId().trim().isEmpty()) {
            return dto.getClientId().trim();
        }
        return dto.getInstanceId();
    }

    private String resolveDeviceType(ServiceRegisterDTO dto) {
        if (dto.getDeviceType() != null && !dto.getDeviceType().trim().isEmpty()) {
            return dto.getDeviceType().trim();
        }
        return dto.getServiceName();
    }

    private String buildDeviceName(ServiceRegisterDTO dto) {
        String serviceName = dto.getServiceName() == null ? "service" : dto.getServiceName();
        return serviceName + "(" + dto.getHost() + ")";
    }

    private String normalizeStatus(String status) {
        if ("UP".equalsIgnoreCase(status)) {
            return STATUS_ONLINE;
        }
        if ("DOWN".equalsIgnoreCase(status)) {
            return STATUS_OFFLINE;
        }
        return status;
    }

    private ServiceInstance findExistingInstance(ServiceRegisterDTO dto) {
        String deviceId = resolveDeviceId(dto);
        if (deviceId != null && !deviceId.trim().isEmpty()) {
            LambdaQueryWrapper<ServiceInstance> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ServiceInstance::getDeviceId, deviceId.trim());
            return serviceInstanceMapper.selectOne(wrapper);
        }
        return null;
    }
}
