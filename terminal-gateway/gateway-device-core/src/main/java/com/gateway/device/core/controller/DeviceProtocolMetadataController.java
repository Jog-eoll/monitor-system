package com.gateway.device.core.controller;

import com.gateway.common.Result;
import com.gateway.device.core.service.DeviceProtocolMetadataService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/api/device-protocol")
public class DeviceProtocolMetadataController {

    @Resource
    private DeviceProtocolMetadataService deviceProtocolMetadataService;

    @GetMapping("/metadata")
    public Result<Map<String, Object>> metadata(@RequestParam(required = false) String vendor) {
        return Result.success(deviceProtocolMetadataService.metadata(vendor));
    }
}
