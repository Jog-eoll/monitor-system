package com.monitorplatform.content.entity.vo;

import lombok.Data;

import java.util.List;

@Data
public class BoardMonitorCardVO {
    private DeviceInfoVO deviceInfo;
    private List<ContentSummaryVO> latestContents;
    private AlarmRecordVO activeAlarm;
    private Long chainId;
    private ScreenStatusVO screenStatus;
}
