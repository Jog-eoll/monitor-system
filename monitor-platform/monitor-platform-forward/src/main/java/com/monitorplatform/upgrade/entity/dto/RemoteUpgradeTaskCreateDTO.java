package com.monitorplatform.upgrade.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 创建远程升级任务请求。
 */
@Data
public class RemoteUpgradeTaskCreateDTO {

    private String taskName;

    private String packageName;

    @NotNull(message = "packageId不能为空")
    private Long packageId;

    @NotEmpty(message = "targetDeviceIds不能为空")
    private List<String> targetDeviceIds;

    private Boolean rollbackEnabled = true;

    private Integer timeoutSeconds = 1800;

    private String operator;

    private String remark;
}
