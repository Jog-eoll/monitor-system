package com.monitorplatform.content.feign;

import com.monitorplatform.alarm.entity.AlarmRecord;
import com.monitorplatform.alarm.entity.dto.AlarmReceiveDTO;
import com.monitorplatform.alarm.service.AlarmService;
import com.monitorplatform.content.entity.dto.AlarmReceiveRequestDTO;
import com.monitorplatform.content.entity.vo.AlarmRecordVO;
import com.monitorplatform.content.entity.vo.CommonServiceResponseVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Alarm 服务本地调用（替代原 Feign 客户端）
 */
@Slf4j
@Service("contentAlarmFeignClient")
public class AlarmFeignClient {

    @Resource
    private AlarmService alarmService;

    public CommonServiceResponseVO<List<AlarmRecordVO>> getPendingList(Integer limit) {
        CommonServiceResponseVO<List<AlarmRecordVO>> resp = new CommonServiceResponseVO<>();
        try {
            List<AlarmRecord> records = alarmService.getPendingList(limit != null ? limit : 20);
            List<AlarmRecordVO> voList = new ArrayList<>();
            if (records != null) {
                for (AlarmRecord r : records) {
                    AlarmRecordVO vo = new AlarmRecordVO();
                    vo.setId(r.getId());
                    vo.setAlarmType(r.getAlarmType());
                    vo.setAlarmLevel(r.getAlarmLevel());
                    vo.setDeviceId(r.getDeviceId());
                    vo.setChainId(r.getChainId());
                    vo.setBoardIp(r.getBoardIp());
                    vo.setBoardPort(r.getBoardPort());
                    vo.setDeviceName(r.getDeviceName());
                    vo.setContentId(r.getContentId());
                    vo.setViolationType(r.getViolationType());
                    vo.setViolationDetail(r.getViolationDetail());
                    vo.setAlarmTime(r.getAlarmTime());
                    vo.setHandleStatus(r.getHandleStatus());
                    voList.add(vo);
                }
            }
            resp.setCode(200);
            resp.setData(voList);
        } catch (Exception e) {
            log.error("获取待处理告警列表失败", e);
            resp.setCode(500);
            resp.setMsg("获取失败: " + e.getMessage());
        }
        return resp;
    }

    public CommonServiceResponseVO<Object> receiveAlarm(AlarmReceiveRequestDTO request) {
        CommonServiceResponseVO<Object> resp = new CommonServiceResponseVO<>();
        try {
            AlarmReceiveDTO dto = new AlarmReceiveDTO();
            dto.setAlarmType(request.getAlarmType());
            dto.setAlarmLevel(request.getAlarmLevel());
            dto.setChainId(request.getChainId());
            dto.setBoardIp(request.getBoardIp());
            dto.setBoardPort(request.getBoardPort());
            dto.setDeviceId(request.getDeviceId());
            dto.setDeviceName(request.getDeviceName());
            dto.setContentId(request.getContentId());
            dto.setViolationType(request.getViolationType());
            dto.setViolationDetail(request.getViolationDetail());
            if (request.getAlarmTime() != null) {
                dto.setAlarmTime(LocalDateTime.parse(request.getAlarmTime()));
            } else {
                dto.setAlarmTime(LocalDateTime.now());
            }
            AlarmRecord record = alarmService.receiveAlarm(dto);
            resp.setCode(200);
            resp.setMsg("告警接收成功");
            resp.setData(record);
        } catch (Exception e) {
            log.error("接收告警失败", e);
            resp.setCode(500);
            resp.setMsg("接收告警失败: " + e.getMessage());
        }
        return resp;
    }
}
