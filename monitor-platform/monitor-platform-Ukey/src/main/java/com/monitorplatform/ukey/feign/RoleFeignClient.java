package com.monitorplatform.ukey.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "monitor-role", contextId = "ukeyRoleFeignClient")
public interface RoleFeignClient {

    @GetMapping("/role/internal/auth-context/by-ukey-id")
    Map<String, Object> getAuthContextByUkeyId(@RequestParam("ukeyId") String ukeyId);
}
