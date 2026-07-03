package com.monitorplatform.content.feign;

import com.monitorplatform.content.entity.dto.AlarmReceiveRequestDTO;
import com.monitorplatform.content.entity.vo.AlarmRecordVO;
import com.monitorplatform.content.entity.vo.CommonServiceResponseVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "monitor-alarm")
public interface AlarmFeignClient {

    @GetMapping("/alarm/pending")
    CommonServiceResponseVO<List<AlarmRecordVO>> getPendingList(@RequestParam("limit") Integer limit);

    @PostMapping("/alarm/receive")
    CommonServiceResponseVO<Object> receiveAlarm(@RequestBody AlarmReceiveRequestDTO request);
}
