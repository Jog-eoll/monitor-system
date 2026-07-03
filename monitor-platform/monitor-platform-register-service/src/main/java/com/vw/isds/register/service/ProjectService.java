package com.vw.isds.register.service;


import com.monitorplatform.common.constant.ServiceNameConstants;
import com.vw.isds.register.entity.ProjectInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.HashMap;

/**
 * @Description: java类描述
 * @Author: zqr
 * @Date: 2024/2/23 15:15:43
 */
@Component
@FeignClient(value = ServiceNameConstants.USER_SERVICE, path = "/core/project")
public interface ProjectService {

    @PostMapping("/updateKey")
    void updateKey(@RequestBody HashMap<String, String> map);


    @GetMapping("/info")
    ProjectInfo info();
}
