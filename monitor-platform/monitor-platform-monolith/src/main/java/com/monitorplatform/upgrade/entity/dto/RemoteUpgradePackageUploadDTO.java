package com.monitorplatform.upgrade.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 远程升级包上传请求。
 */
@Data
public class RemoteUpgradePackageUploadDTO {

    @NotBlank(message = "packageName不能为空")
    private String packageName;

    @NotBlank(message = "packageType不能为空")
    private String packageType;

    @NotBlank(message = "deviceType不能为空")
    private String deviceType;

    @NotBlank(message = "targetVersion不能为空")
    private String targetVersion;

    private String remark;
}
