package com.monitorplatform.role.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.monitorplatform.common.entity.SysUserLogVO;
import com.monitorplatform.role.entity.SysUserLog;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * <p>
 * 系统管理-操作日志表 服务类
 * </p>
 *
 * @author suweiming
 * @since 2022-07-13
 */
public interface ISysUserLogService extends IService<SysUserLog> {
    /**
     * 新增日志
     * @param item
     * @return
     */
    String insert(@RequestBody SysUserLogVO item);
}
