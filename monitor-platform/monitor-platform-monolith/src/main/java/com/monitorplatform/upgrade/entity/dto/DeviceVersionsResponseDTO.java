package com.monitorplatform.upgrade.entity.dto;

import lombok.Data;

import java.util.List;

@Data
public class DeviceVersionsResponseDTO {

    private String targetDeviceId;

    private String currentVersion;

    private int total;

    private List<DeviceVersionRecordDTO> records;
}
