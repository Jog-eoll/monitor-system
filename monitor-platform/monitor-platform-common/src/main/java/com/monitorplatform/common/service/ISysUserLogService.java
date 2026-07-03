package com.monitorplatform.common.service;


import com.monitorplatform.common.constant.ServiceNameConstants;
import com.monitorplatform.common.entity.SysUserLogVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Component
@FeignClient(value = ServiceNameConstants.USER_SERVICE, contextId = "sysUserLogFeignClient")
public interface ISysUserLogService {
    /**
     * 新增日志
     */
    @PostMapping(value = "/role/sysUserLog/insert")
    void insert(@RequestBody SysUserLogVO item);
}
