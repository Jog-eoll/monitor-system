package com.monitorplatform.registry.controller;

import com.monitorplatform.common.entity.Result;
import com.monitorplatform.registry.dto.ServiceRegisterDTO;
import com.monitorplatform.registry.entity.ServiceInstance;
import com.monitorplatform.registry.service.ServiceRegistryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/registry")
public class ServiceRegistryController {

    @Resource
    private ServiceRegistryService serviceRegistryService;

    @PostMapping("/register")
    public Result<?> registerService(@Valid @RequestBody ServiceRegisterDTO dto) {
        boolean success = serviceRegistryService.registerService(dto);
        return success ? Result.success() : Result.error("service register failed");
    }

    @DeleteMapping("/deregister/{instanceId}")
    public Result<?> deregisterService(@PathVariable String instanceId) {
        boolean success = serviceRegistryService.deregisterService(instanceId);
        return success ? Result.success() : Result.error("service deregister failed");
    }

    @PostMapping("/heartbeat/{instanceId}")
    public Result<?> heartbeat(@PathVariable String instanceId) {
        boolean success = serviceRegistryService.heartbeat(instanceId);
        return success ? Result.success() : Result.error("heartbeat failed");
    }

    @GetMapping("/discover/{serviceName}")
    public Result<?> discoverServices(@PathVariable String serviceName) {
        List<ServiceInstance> instances = serviceRegistryService.discoverServices(serviceName);
        return Result.success(instances);
    }

    @GetMapping("/instance/{instanceId}")
    public Result<?> getServiceInstance(@PathVariable String instanceId) {
        ServiceInstance instance = serviceRegistryService.getServiceInstance(instanceId);
        return instance != null ? Result.success(instance) : Result.error("service instance not found");
    }

    @GetMapping("/instance/by-client/{clientId}")
    public Result<?> getServiceInstanceByClientId(@PathVariable String clientId) {
        ServiceInstance instance = serviceRegistryService.getServiceInstanceByClientId(clientId);
        return instance != null ? Result.success(instance) : Result.error("service instance not found");
    }

    @GetMapping("/services")
    public Result<?> getAllServices() {
        List<ServiceInstance> services = serviceRegistryService.getAllServices();
        return Result.success(services);
    }

    @GetMapping("/service-names")
    public Result<?> getAllServiceNames() {
        List<String> serviceNames = serviceRegistryService.getAllServiceNames();
        return Result.success(serviceNames);
    }

    @PutMapping("/status/{instanceId}/{status}")
    public Result<?> updateServiceStatus(@PathVariable String instanceId,
                                                   @PathVariable String status) {
        boolean success = serviceRegistryService.updateServiceStatus(instanceId, status);
        return success ? Result.success() : Result.error("update service status failed");
    }

    @GetMapping("/statistics")
    public Result<?> getServiceStatistics() {
        return Result.success(serviceRegistryService.getServiceStatistics());
    }

    @DeleteMapping("/services/{serviceName}")
    public Result<?> batchDeregisterByServiceName(@PathVariable String serviceName) {
        int count = serviceRegistryService.batchDeregisterByServiceName(serviceName);
        log.info("batch deregister service instances: serviceName={}, count={}", serviceName, count);
        return Result.success();
    }
}
