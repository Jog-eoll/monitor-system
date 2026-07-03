package com.monitorplatform.content.feign;

import com.monitorplatform.content.entity.dto.DeviceRegisterDTO;
import com.monitorplatform.content.entity.dto.InfoBoardBatchQueryDTO;
import com.monitorplatform.content.entity.vo.CommonServiceResponseVO;
import com.monitorplatform.content.entity.vo.DeviceInfoVO;
import com.monitorplatform.content.entity.vo.DevicePageDataVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "monitor-device")
public interface DeviceFeignClient {

    @GetMapping("/device/unified/page")
    CommonServiceResponseVO<DevicePageDataVO> pageInfoBoards(@RequestParam("deviceType") String deviceType,
                                                             @RequestParam("pageNum") Integer pageNum,
                                                             @RequestParam("pageSize") Integer pageSize,
                                                             @RequestParam(value = "keyword", required = false) String keyword);

    @PostMapping("/device/unified/info-board/batch")
    CommonServiceResponseVO<List<DeviceInfoVO>> listInfoBoardsByDeviceIds(@RequestBody InfoBoardBatchQueryDTO params);

    @PostMapping("/device/registry/auto-register")
    CommonServiceResponseVO<Void> autoRegisterDevice(@RequestBody DeviceRegisterDTO params);
}
