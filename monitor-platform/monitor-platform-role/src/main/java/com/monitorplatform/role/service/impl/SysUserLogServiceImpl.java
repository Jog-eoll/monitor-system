package com.monitorplatform.role.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.monitorplatform.common.entity.SysUserLogVO;
import com.monitorplatform.role.entity.SysUserLog;
import com.monitorplatform.role.mapper.SysUserLogMapper;
import com.monitorplatform.role.service.ISysUserLogService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 系统管理-操作日志表 服务实现类
 * </p>
 *
 * @author suweiming
 * @since 2022-07-13
 */
@Service
public class SysUserLogServiceImpl extends ServiceImpl<SysUserLogMapper, SysUserLog> implements ISysUserLogService {

    @Override
    public String insert(SysUserLogVO item) {
        SysUserLog sysUser = new SysUserLog();
        BeanUtils.copyProperties(item, sysUser);
        this.save(sysUser);
        item.setId(sysUser.getId());
        return  item.getId();
    }
}
