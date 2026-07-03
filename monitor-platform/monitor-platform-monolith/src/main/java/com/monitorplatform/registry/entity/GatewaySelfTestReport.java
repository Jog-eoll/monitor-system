package com.monitorplatform.registry.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("gateway_self_test_report")
public class GatewaySelfTestReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceId;
    private String overallStatus;
    private Boolean encryptSampleOk;
    private Boolean decryptSampleOk;
    private Boolean platformHandshakeOk;
    private Boolean ukeyStatusOk;
    private String detailJson;
    private String errorMessage;
    private LocalDateTime reportTime;
    private LocalDateTime createTime;
}
