package com.monitorplatform.monolith.adapter;

import com.monitorplatform.common.entity.SysUserLogVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * ISysUserLogService 本地适配（替代 common 模块的 FeignClient 代理）
 * 将 OperateLogAspect 的日志调用转发到本地 role 模块的 ISysUserLogService
 */
@Slf4j
@Service
public class SysUserLogServiceAdapter implements com.monitorplatform.common.service.ISysUserLogService {

    @Resource
    private com.monitorplatform.role.service.ISysUserLogService roleSysUserLogService;

    @Override
    public void insert(SysUserLogVO item) {
        try {
            roleSysUserLogService.insert(item);
        } catch (Exception e) {
            log.warn("操作日志记录失败: {}", e.getMessage());
        }
    }
}
