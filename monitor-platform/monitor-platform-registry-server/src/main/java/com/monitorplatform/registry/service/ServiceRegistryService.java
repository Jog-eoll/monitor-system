package com.monitorplatform.registry.service;

import com.monitorplatform.registry.dto.ServiceRegisterDTO;
import com.monitorplatform.registry.entity.ServiceInstance;

import java.util.List;
import java.util.Map;

public interface ServiceRegistryService {
    
    boolean registerService(ServiceRegisterDTO dto);
    
    boolean deregisterService(String instanceId);
    
    boolean heartbeat(String instanceId);
    
    List<ServiceInstance> discoverServices(String serviceName);
    
    ServiceInstance getServiceInstance(String instanceId);

    ServiceInstance getServiceInstanceByClientId(String clientId);
    
    List<ServiceInstance> getAllServices();
    
    List<String> getAllServiceNames();
    
    boolean updateServiceStatus(String instanceId, String status);
    
    Map<String, Object> getServiceStatistics();
    
    int batchDeregisterByServiceName(String serviceName);
}
