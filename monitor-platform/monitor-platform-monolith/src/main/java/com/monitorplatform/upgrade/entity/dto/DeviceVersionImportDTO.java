package com.monitorplatform.upgrade.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Data
public class DeviceVersionImportDTO {

    @NotBlank(message = "version不能为空")
    private String version;

    private Long packageId;

    private String packageName;

    private LocalDateTime installedAt;

    private String operator;

    private String remark;
}
