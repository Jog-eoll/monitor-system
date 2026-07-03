package com.monitorplatform.registry.feign;

import com.monitorplatform.registry.dto.UkeyCertValidateRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "monitor-ukey", contextId = "gatewayDeployUkeyClient")
public interface UkeyDeployFeignClient {

    @PostMapping("/cert/validate")
    Map<String, Object> validateCertificate(@RequestBody UkeyCertValidateRequest request);

    @PutMapping("/cert/bind-client")
    Map<String, Object> bindClient(@RequestBody Map<String, String> params);
}
