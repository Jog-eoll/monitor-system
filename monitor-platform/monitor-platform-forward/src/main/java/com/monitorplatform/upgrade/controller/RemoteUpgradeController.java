package com.monitorplatform.upgrade.controller;

import com.monitorplatform.common.entity.Result;
import com.monitorplatform.upgrade.entity.dto.DeviceVersionImportDTO;
import com.monitorplatform.upgrade.entity.dto.DeviceVersionsResponseDTO;
import com.monitorplatform.upgrade.entity.dto.RemoteUpgradePackageUploadDTO;
import com.monitorplatform.upgrade.entity.dto.RemoteUpgradeStartDTO;
import com.monitorplatform.upgrade.entity.dto.RemoteUpgradeTaskCreateDTO;
import com.monitorplatform.upgrade.entity.dto.RollbackDTO;
import com.monitorplatform.upgrade.service.RemoteUpgradeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;

@Slf4j
@RestController
@RequestMapping("/upgrade")
public class RemoteUpgradeController {

    @Resource
    private RemoteUpgradeService remoteUpgradeService;

    @PostMapping("/packages/upload")
    public Result<?> uploadPackage(@Validated @ModelAttribute RemoteUpgradePackageUploadDTO dto,
                                   @RequestParam("file") MultipartFile file) {
        try {
            return Result.success(remoteUpgradeService.uploadPackage(dto, file));
        } catch (Exception e) {
            log.error("上传远程升级包失败", e);
            return Result.error("上传远程升级包失败: " + e.getMessage());
        }
    }

    @GetMapping("/packages")
    public Result<?> listPackages(@RequestParam(value = "deviceType", required = false) String deviceType,
                                  @RequestParam(value = "packageType", required = false) String packageType) {
        return Result.success(remoteUpgradeService.listPackages(deviceType, packageType));
    }

    @GetMapping("/packages/{packageId}/download")
    public void downloadPackage(@PathVariable Long packageId, HttpServletResponse response) {
        remoteUpgradeService.downloadPackage(packageId, response);
    }

    @PostMapping("/tasks")
    public Result<?> createTask(@Validated @RequestBody RemoteUpgradeTaskCreateDTO dto) {
        try {
            return Result.success(remoteUpgradeService.createTask(dto));
        } catch (Exception e) {
            log.error("创建远程升级任务失败", e);
            return Result.error("创建远程升级任务失败: " + e.getMessage());
        }
    }

    @PostMapping("/tasks/{taskId}/start")
    public Result<?> startTask(@PathVariable Long taskId,
                               @RequestBody(required = false) RemoteUpgradeStartDTO dto) {
        try {
            boolean waitForResult = dto != null && Boolean.TRUE.equals(dto.getWaitForResult());
            return Result.success(remoteUpgradeService.startTask(taskId, waitForResult));
        } catch (Exception e) {
            log.error("启动远程升级任务失败: taskId={}", taskId, e);
            return Result.error("启动远程升级任务失败: " + e.getMessage());
        }
    }

    @GetMapping("/tasks/{taskId}")
    public Result<?> getTask(@PathVariable Long taskId) {
        return Result.success(remoteUpgradeService.getTask(taskId));
    }

    @GetMapping("/tasks/{taskId}/devices")
    public Result<?> listTaskDevices(@PathVariable Long taskId) {
        return Result.success(remoteUpgradeService.listTaskDevices(taskId));
    }

    @GetMapping("/devices/{targetDeviceId}/versions")
    public Result<?> listDeviceVersions(@PathVariable String targetDeviceId) {
        try {
            DeviceVersionsResponseDTO response = remoteUpgradeService.listDeviceVersions(targetDeviceId);
            return Result.success(response);
        } catch (Exception e) {
            log.error("查询设备版本历史失败: targetDeviceId={}", targetDeviceId, e);
            return Result.error("查询设备版本历史失败: " + e.getMessage());
        }
    }

    @PostMapping("/devices/{targetDeviceId}/versions/import")
    public Result<?> importDeviceVersion(@PathVariable String targetDeviceId,
                                         @Validated @RequestBody DeviceVersionImportDTO dto) {
        try {
            return Result.success(remoteUpgradeService.importDeviceVersion(targetDeviceId, dto));
        } catch (Exception e) {
            log.error("导入设备历史版本失败: targetDeviceId={}", targetDeviceId, e);
            return Result.error("导入设备历史版本失败: " + e.getMessage());
        }
    }

    @PostMapping("/devices/{targetDeviceId}/rollback")
    public Result<?> rollbackToVersion(@PathVariable String targetDeviceId,
                                       @Validated @RequestBody RollbackDTO dto) {
        try {
            return Result.success(remoteUpgradeService.rollbackToVersion(targetDeviceId, dto));
        } catch (Exception e) {
            log.error("回滚设备版本失败: targetDeviceId={}", targetDeviceId, e);
            return Result.error("回滚设备版本失败: " + e.getMessage());
        }
    }
}
