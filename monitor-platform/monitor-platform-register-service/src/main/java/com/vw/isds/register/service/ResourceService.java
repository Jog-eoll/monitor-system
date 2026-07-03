package com.vw.isds.register.service;


import com.monitorplatform.common.constant.ServiceNameConstants;
import com.vw.isds.register.entity.SysResource;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * @Description: java类描述
 * @Author: zqr
 * @Date: 2024/2/26 18:11:19
 */
@Component
@FeignClient(value = ServiceNameConstants.USER_SERVICE)
@RequestMapping("/upms/resource")
public interface ResourceService {

    @PostMapping("/updateResource")
    void updateResource(@RequestBody List<SysResource> resources);
}
