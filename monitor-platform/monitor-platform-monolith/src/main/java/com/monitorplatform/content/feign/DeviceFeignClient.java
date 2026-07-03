package com.monitorplatform.content.feign;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitorplatform.device.entity.dto.UnifiedDeviceDTO;
import com.monitorplatform.device.service.UnifiedDeviceService;
import com.monitorplatform.content.entity.vo.CommonServiceResponseVO;
import com.monitorplatform.content.entity.vo.DeviceInfoVO;
import com.monitorplatform.content.entity.vo.DevicePageDataVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Device 服务本地调用（替代原 Feign 客户端）
 */
@Slf4j
@Service("contentDeviceFeignClient")
public class DeviceFeignClient {

    @Resource
    private UnifiedDeviceService unifiedDeviceService;

    public CommonServiceResponseVO<DevicePageDataVO> pageInfoBoards(String deviceType,
                                                                     Integer pageNum,
                                                                     Integer pageSize,
                                                                     String keyword) {
        CommonServiceResponseVO<DevicePageDataVO> resp = new CommonServiceResponseVO<>();
        try {
            Page<UnifiedDeviceDTO> page = unifiedDeviceService.pageListAllDevices(
                    pageNum != null ? pageNum : 1,
                    pageSize != null ? pageSize : 10,
                    deviceType, null, keyword);
            DevicePageDataVO vo = new DevicePageDataVO();
            List<DeviceInfoVO> records = page.getRecords().stream()
                    .map(dto -> BeanUtil.copyProperties(dto, DeviceInfoVO.class))
                    .collect(Collectors.toList());
            vo.setRecords(records);
            vo.setTotal(page.getTotal());
            vo.setCurrent(page.getCurrent());
            vo.setSize(page.getSize());
            vo.setPages(page.getPages());
            resp.setCode(200);
            resp.setData(vo);
        } catch (Exception e) {
            log.error("查询设备分页数据失败", e);
            resp.setCode(500);
            resp.setMsg("查询失败: " + e.getMessage());
        }
        return resp;
    }
}
