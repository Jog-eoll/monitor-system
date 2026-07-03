package com.monitorplatform.registry.client;

import com.monitorplatform.registry.client.dto.ServiceInstance;
import com.monitorplatform.registry.client.dto.ServiceRegisterRequest;

import java.util.List;

public interface ServiceRegistryClient {
    boolean register(ServiceRegisterRequest request);
    
    boolean deregister(String instanceId);
    
    boolean heartbeat(String instanceId);
    
    List<ServiceInstance> discover(String serviceName);
    
    ServiceInstance getInstance(String instanceId);
    
    List<ServiceInstance> getAllServices();
}