package com.monitorplatform.registry.controller;

import com.monitorplatform.common.entity.Result;
import com.monitorplatform.registry.dto.ClientConfigDTO;
import com.monitorplatform.registry.entity.ServiceInstance;
import com.monitorplatform.registry.service.ClientConfigService;
import com.monitorplatform.registry.service.ServiceRegistryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/registry/client-config")
public class ClientConfigController {

    @Resource
    private ClientConfigService clientConfigService;

    @Resource
    private ServiceRegistryService serviceRegistryService;

    @GetMapping("/{clientId}")
    public Result<?> getConfig(@PathVariable String clientId) {
        ServiceInstance instance = serviceRegistryService.getServiceInstanceByClientId(clientId);
        if (instance == null) {
            return Result.build(404, "service instance not registered", null);
        }

        Map<String, Object> data = clientConfigService.getConfigMap(clientId);
        if (data == null) {
            return Result.build(404, "client config not found or disabled", null);
        }
        return Result.success(data);
    }

    @PostMapping("/{clientId}")
    public Result<?> saveConfig(@PathVariable String clientId,
                                          @Valid @RequestBody ClientConfigDTO dto) {
        dto.setClientId(clientId);
        return Result.success(clientConfigService.saveConfig(dto));
    }
}
