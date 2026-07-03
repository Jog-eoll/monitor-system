package com.monitorplatform.upgrade.entity.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeviceVersionRecordDTO {

    private String version;

    private String status;

    private Long packageId;

    private String packageName;

    private Long taskId;

    private String taskName;

    private String packageType;

    private Long fileSize;

    private String sha256;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime installedAt;

    private String durationText;

    private String operator;

    private Boolean rollbackable;

    private String remark;
}
