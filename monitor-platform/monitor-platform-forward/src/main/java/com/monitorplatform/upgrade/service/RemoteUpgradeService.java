package com.monitorplatform.upgrade.service;

import com.monitorplatform.upgrade.entity.RemoteUpgradePackage;
import com.monitorplatform.upgrade.entity.RemoteUpgradeTask;
import com.monitorplatform.upgrade.entity.RemoteUpgradeTaskDevice;
import com.monitorplatform.upgrade.entity.dto.DeviceVersionImportDTO;
import com.monitorplatform.upgrade.entity.dto.DeviceVersionRecordDTO;
import com.monitorplatform.upgrade.entity.dto.DeviceVersionsResponseDTO;
import com.monitorplatform.upgrade.entity.dto.RollbackDTO;
import com.monitorplatform.upgrade.entity.dto.RemoteUpgradePackageUploadDTO;
import com.monitorplatform.upgrade.entity.dto.RemoteUpgradeTaskCreateDTO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

public interface RemoteUpgradeService {

    RemoteUpgradePackage uploadPackage(RemoteUpgradePackageUploadDTO dto, MultipartFile file);

    List<RemoteUpgradePackage> listPackages(String deviceType, String packageType);

    RemoteUpgradeTask createTask(RemoteUpgradeTaskCreateDTO dto);

    Map<String, Object> startTask(Long taskId, boolean waitForResult);

    RemoteUpgradeTask getTask(Long taskId);

    List<RemoteUpgradeTaskDevice> listTaskDevices(Long taskId);

    void downloadPackage(Long packageId, HttpServletResponse response);

    DeviceVersionsResponseDTO listDeviceVersions(String targetDeviceId);

    RemoteUpgradeTaskDevice importDeviceVersion(String targetDeviceId, DeviceVersionImportDTO dto);

    Map<String, Object> rollbackToVersion(String targetDeviceId, RollbackDTO dto);
}
