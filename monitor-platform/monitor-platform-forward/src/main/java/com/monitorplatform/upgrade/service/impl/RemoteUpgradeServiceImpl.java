package com.monitorplatform.upgrade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.monitorplatform.forward.entity.DeviceMqttCommand;
import com.monitorplatform.forward.service.MqttCommandPublishService;
import com.monitorplatform.upgrade.entity.RemoteUpgradeConstants;
import com.monitorplatform.upgrade.entity.RemoteUpgradePackage;
import com.monitorplatform.upgrade.entity.RemoteUpgradeTask;
import com.monitorplatform.upgrade.entity.RemoteUpgradeTaskDevice;
import com.monitorplatform.upgrade.entity.dto.DeviceVersionImportDTO;
import com.monitorplatform.upgrade.entity.dto.DeviceVersionRecordDTO;
import com.monitorplatform.upgrade.entity.dto.DeviceVersionsResponseDTO;
import com.monitorplatform.upgrade.entity.dto.RollbackDTO;
import com.monitorplatform.upgrade.entity.dto.RemoteUpgradePackageUploadDTO;
import com.monitorplatform.upgrade.entity.dto.RemoteUpgradeTaskCreateDTO;
import com.monitorplatform.upgrade.mapper.RemoteUpgradePackageMapper;
import com.monitorplatform.upgrade.mapper.RemoteUpgradeTaskDeviceMapper;
import com.monitorplatform.upgrade.mapper.RemoteUpgradeTaskMapper;
import com.monitorplatform.upgrade.service.RemoteUpgradeService;
import io.minio.MinioClient;
import io.minio.GetObjectArgs;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 远程升级服务实现。
 */
@Slf4j
@Service
public class RemoteUpgradeServiceImpl implements RemoteUpgradeService {

    private static final DateTimeFormatter CODE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Resource
    private RemoteUpgradePackageMapper packageMapper;

    @Resource
    private RemoteUpgradeTaskMapper taskMapper;

    @Resource
    private RemoteUpgradeTaskDeviceMapper taskDeviceMapper;

    @Resource
    private MqttCommandPublishService mqttCommandPublishService;

    @Resource
    private MinioClient minioClient;

    @Value("${minio.bucket-name:monitor-platform}")
    private String minioBucketName;

    @Value("${upgrade.download-base-url:}")
    private String upgradeDownloadBaseUrl;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RemoteUpgradePackage uploadPackage(RemoteUpgradePackageUploadDTO dto, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("升级包文件不能为空");
        }
        try {
            String packageCode = "PKG-" + CODE_TIME_FORMAT.format(LocalDateTime.now()) + "-"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String originalFilename = file.getOriginalFilename();
            String objectName = "upgrade/" + packageCode + "/" + sanitizeFilename(originalFilename);
            String sha256 = sha256(file);

            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(minioBucketName)
                        .object(objectName)
                        .stream(inputStream, file.getSize(), -1)
                        .contentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType())
                        .build());
            }

            LocalDateTime now = LocalDateTime.now();
            RemoteUpgradePackage pkg = new RemoteUpgradePackage();
            pkg.setPackageCode(packageCode);
            pkg.setPackageName(dto.getPackageName());
            pkg.setPackageType(dto.getPackageType());
            pkg.setDeviceType(dto.getDeviceType());
            pkg.setTargetVersion(dto.getTargetVersion());
            pkg.setStorageType("MINIO");
            pkg.setObjectName(objectName);
            pkg.setDownloadUrl(buildDownloadUrl(objectName));
            pkg.setSha256(sha256);
            pkg.setFileSize(file.getSize());
            pkg.setStatus(RemoteUpgradeConstants.PACKAGE_STATUS_ACTIVE);
            pkg.setRemark(dto.getRemark());
            pkg.setCreateTime(now);
            pkg.setUpdateTime(now);
            packageMapper.insert(pkg);
            refreshDownloadUrl(pkg);
            pkg.setUpdateTime(LocalDateTime.now());
            packageMapper.updateById(pkg);
            return pkg;
        } catch (Exception e) {
            log.error("上传远程升级包失败", e);
            throw new IllegalStateException("上传远程升级包失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<RemoteUpgradePackage> listPackages(String deviceType, String packageType) {
        LambdaQueryWrapper<RemoteUpgradePackage> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(deviceType)) {
            wrapper.eq(RemoteUpgradePackage::getDeviceType, deviceType.trim());
        }
        if (StringUtils.hasText(packageType)) {
            wrapper.eq(RemoteUpgradePackage::getPackageType, packageType.trim());
        }
        wrapper.orderByDesc(RemoteUpgradePackage::getCreateTime);
        return packageMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RemoteUpgradeTask createTask(RemoteUpgradeTaskCreateDTO dto) {
        RemoteUpgradePackage pkg = packageMapper.selectById(dto.getPackageId());
        if (pkg == null) {
            throw new IllegalArgumentException("升级包不存在: " + dto.getPackageId());
        }
        List<String> targetDeviceIds = normalizeDeviceIds(dto.getTargetDeviceIds());
        if (targetDeviceIds.isEmpty()) {
            throw new IllegalArgumentException("targetDeviceIds不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        String taskCode = "UPG-" + CODE_TIME_FORMAT.format(now) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        RemoteUpgradeTask task = new RemoteUpgradeTask();
        task.setTaskCode(taskCode);
        task.setTaskName(StringUtils.hasText(dto.getTaskName()) ? dto.getTaskName() : "远程升级-" + taskCode);
        task.setPackageId(pkg.getId());
        task.setPackageCode(pkg.getPackageCode());
        task.setPackageType(pkg.getPackageType());
        task.setTargetVersion(pkg.getTargetVersion());
        task.setStatus(RemoteUpgradeConstants.TASK_STATUS_CREATED);
        task.setTotalCount(targetDeviceIds.size());
        task.setSuccessCount(0);
        task.setFailedCount(0);
        task.setTimeoutCount(0);
        task.setRollbackEnabled(!Boolean.FALSE.equals(dto.getRollbackEnabled()));
        task.setTimeoutSeconds(dto.getTimeoutSeconds() == null ? 1800 : dto.getTimeoutSeconds());
        task.setOperator(dto.getOperator());
        task.setRemark(dto.getRemark());
        task.setCreateTime(now);
        task.setUpdateTime(now);
        taskMapper.insert(task);

        for (String deviceId : targetDeviceIds) {
            RemoteUpgradeTaskDevice item = new RemoteUpgradeTaskDevice();
            item.setTaskId(task.getId());
            item.setTaskCode(taskCode);
            item.setTargetDeviceId(deviceId);
            item.setDeviceType(pkg.getDeviceType());
            item.setTargetVersion(pkg.getTargetVersion());
            item.setStatus(RemoteUpgradeConstants.DEVICE_STATUS_CREATED);
            item.setStage("CREATED");
            item.setProgress(0);
            item.setCreateTime(now);
            item.setUpdateTime(now);
            taskDeviceMapper.insert(item);
        }
        return task;
    }

    @Override
    public Map<String, Object> startTask(Long taskId, boolean waitForResult) {
        RemoteUpgradeTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("升级任务不存在: " + taskId);
        }
        RemoteUpgradePackage pkg = packageMapper.selectById(task.getPackageId());
        if (pkg == null) {
            throw new IllegalStateException("升级包不存在: " + task.getPackageId());
        }

        List<RemoteUpgradeTaskDevice> devices = listTaskDevices(taskId);
        if (devices.isEmpty()) {
            throw new IllegalStateException("升级任务没有设备明细: " + taskId);
        }

        LocalDateTime now = LocalDateTime.now();
        task.setStatus(RemoteUpgradeConstants.TASK_STATUS_RUNNING);
        task.setStartTime(task.getStartTime() == null ? now : task.getStartTime());
        task.setUpdateTime(now);
        taskMapper.updateById(task);

        List<Map<String, Object>> publishResults = new ArrayList<>();
        for (RemoteUpgradeTaskDevice device : devices) {
            if (!RemoteUpgradeConstants.DEVICE_STATUS_CREATED.equals(device.getStatus())) {
                continue;
            }
            Map<String, Object> result = publishUpgradeCommand(task, pkg, device, waitForResult);
            publishResults.add(result);
        }
        refreshTaskSummary(taskId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("taskId", taskId);
        response.put("taskCode", task.getTaskCode());
        response.put("published", publishResults);
        return response;
    }

    @Override
    public RemoteUpgradeTask getTask(Long taskId) {
        return taskMapper.selectById(taskId);
    }

    @Override
    public List<RemoteUpgradeTaskDevice> listTaskDevices(Long taskId) {
        LambdaQueryWrapper<RemoteUpgradeTaskDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RemoteUpgradeTaskDevice::getTaskId, taskId)
                .orderByAsc(RemoteUpgradeTaskDevice::getId);
        return taskDeviceMapper.selectList(wrapper);
    }

    @Override
    public void downloadPackage(Long packageId, HttpServletResponse response) {
        RemoteUpgradePackage pkg = packageMapper.selectById(packageId);
        if (pkg == null || !StringUtils.hasText(pkg.getObjectName())) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String filename = pkg.getObjectName();
        int slashIndex = filename.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex < filename.length() - 1) {
            filename = filename.substring(slashIndex + 1);
        }
        response.setContentType("application/octet-stream");
        if (pkg.getFileSize() != null && pkg.getFileSize() >= 0) {
            response.setContentLengthLong(pkg.getFileSize());
        }
        try {
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + URLEncoder.encode(filename, "UTF-8") + "\"");
            try (InputStream inputStream = openMinioObject(pkg.getObjectName())) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = inputStream.read(buffer)) > 0) {
                    response.getOutputStream().write(buffer, 0, len);
                }
                response.flushBuffer();
            }
        } catch (Exception e) {
            log.error("下载远程升级包失败: packageId={}", packageId, e);
            if (!response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
    }

    private InputStream openMinioObject(String objectName) throws Exception {
        Exception lastError = null;
        for (int i = 1; i <= 2; i++) {
            try {
                return minioClient.getObject(GetObjectArgs.builder()
                        .bucket(minioBucketName)
                        .object(objectName)
                        .build());
            } catch (Exception e) {
                lastError = e;
                log.warn("打开远程升级包对象失败，准备重试: objectName={}, attempt={}, error={}",
                        objectName, i, e.getMessage());
            }
        }
        throw lastError;
    }

    private Map<String, Object> publishUpgradeCommand(RemoteUpgradeTask task,
                                                       RemoteUpgradePackage pkg,
                                                       RemoteUpgradeTaskDevice taskDevice,
                                                       boolean waitForResult) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> payload = buildUpgradePayload(task, pkg, taskDevice);
        DeviceMqttCommand command = mqttCommandPublishService.publishCommand(
                taskDevice.getTargetDeviceId(),
                RemoteUpgradeConstants.COMMAND_REMOTE_UPGRADE,
                payload,
                new ArrayList<>());

        if (command == null || command.getId() == null) {
            taskDevice.setStatus(RemoteUpgradeConstants.DEVICE_STATUS_FAILED);
            taskDevice.setStage("PUBLISH_FAILED");
            taskDevice.setProgress(0);
            taskDevice.setErrorMessage("MQTT命令发布失败");
            taskDevice.setUpdateTime(now);
            taskDeviceMapper.updateById(taskDevice);
            return deviceResult(taskDevice, false, "MQTT命令发布失败");
        }

        taskDevice.setMqttCommandId(command.getId());
        taskDevice.setMqttMessageId(command.getMessageId());
        taskDevice.setStatus(RemoteUpgradeConstants.DEVICE_STATUS_PUBLISHED);
        taskDevice.setStage("PUBLISHED");
        taskDevice.setProgress(5);
        taskDevice.setCommandTime(now);
        taskDevice.setUpdateTime(now);
        taskDeviceMapper.updateById(taskDevice);

        if (waitForResult) {
            DeviceMqttCommand finalCommand = mqttCommandPublishService.waitForFinalStatus(
                    command.getId(), task.getTimeoutSeconds() == null ? 1800 : task.getTimeoutSeconds());
            syncDeviceFromMqtt(taskDevice.getId(), finalCommand);
        }
        return deviceResult(taskDevice, true, "已发布");
    }

    private Map<String, Object> buildUpgradePayload(RemoteUpgradeTask task,
                                                     RemoteUpgradePackage pkg,
                                                     RemoteUpgradeTaskDevice device) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.getTaskCode());
        payload.put("taskDbId", task.getId());
        payload.put("taskDeviceId", device.getId());
        payload.put("packageId", pkg.getPackageCode());
        payload.put("packageDbId", pkg.getId());
        payload.put("packageName", pkg.getPackageName());
        payload.put("packageType", pkg.getPackageType());
        payload.put("targetVersion", pkg.getTargetVersion());
        payload.put("downloadUrl", pkg.getDownloadUrl());
        payload.put("sha256", pkg.getSha256());
        payload.put("fileSize", pkg.getFileSize());
        payload.put("rollbackEnabled", Boolean.TRUE.equals(task.getRollbackEnabled()));
        payload.put("timeoutSeconds", task.getTimeoutSeconds() == null ? 1800 : task.getTimeoutSeconds());
        return payload;
    }

    private void syncDeviceFromMqtt(Long taskDeviceId, DeviceMqttCommand command) {
        RemoteUpgradeTaskDevice current = taskDeviceMapper.selectById(taskDeviceId);
        if (current == null || command == null) {
            return;
        }
        current.setAckTime(command.getAckTime());
        current.setUpdateTime(LocalDateTime.now());
        if (DeviceMqttCommand.STATUS_SUCCESS.equals(command.getStatus())) {
            current.setStatus(RemoteUpgradeConstants.DEVICE_STATUS_SUCCESS);
            current.setStage("SUCCESS");
            current.setProgress(100);
            current.setFinishTime(LocalDateTime.now());
        } else if (DeviceMqttCommand.STATUS_FAILED.equals(command.getStatus())) {
            current.setStatus(RemoteUpgradeConstants.DEVICE_STATUS_FAILED);
            current.setStage("FAILED");
            current.setErrorMessage(command.getErrorMessage());
        } else if (DeviceMqttCommand.STATUS_TIMEOUT.equals(command.getStatus())) {
            current.setStatus(RemoteUpgradeConstants.DEVICE_STATUS_TIMEOUT);
            current.setStage("TIMEOUT");
            current.setErrorMessage(command.getErrorMessage());
        } else if (DeviceMqttCommand.STATUS_PROCESSING.equals(command.getStatus())) {
            current.setStatus(RemoteUpgradeConstants.DEVICE_STATUS_PROCESSING);
            current.setStage("PROCESSING");
            current.setProgress(Math.max(current.getProgress() == null ? 0 : current.getProgress(), 10));
        }
        taskDeviceMapper.updateById(current);
    }

    private void refreshTaskSummary(Long taskId) {
        List<RemoteUpgradeTaskDevice> devices = listTaskDevices(taskId);
        int success = 0;
        int failed = 0;
        int timeout = 0;
        for (RemoteUpgradeTaskDevice device : devices) {
            if (RemoteUpgradeConstants.DEVICE_STATUS_SUCCESS.equals(device.getStatus())) {
                success++;
            } else if (RemoteUpgradeConstants.DEVICE_STATUS_FAILED.equals(device.getStatus())) {
                failed++;
            } else if (RemoteUpgradeConstants.DEVICE_STATUS_TIMEOUT.equals(device.getStatus())) {
                timeout++;
            }
        }
        RemoteUpgradeTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        task.setSuccessCount(success);
        task.setFailedCount(failed);
        task.setTimeoutCount(timeout);
        if (success == devices.size()) {
            task.setStatus(RemoteUpgradeConstants.TASK_STATUS_SUCCESS);
            task.setFinishTime(LocalDateTime.now());
        } else if (failed + timeout == devices.size()) {
            task.setStatus(RemoteUpgradeConstants.TASK_STATUS_FAILED);
            task.setFinishTime(LocalDateTime.now());
        } else if (success + failed + timeout == devices.size()) {
            task.setStatus(RemoteUpgradeConstants.TASK_STATUS_PARTIAL_FAILED);
            task.setFinishTime(LocalDateTime.now());
        }
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private List<String> normalizeDeviceIds(List<String> deviceIds) {
        List<String> result = new ArrayList<>();
        if (deviceIds == null) {
            return result;
        }
        for (String deviceId : deviceIds) {
            if (!StringUtils.hasText(deviceId)) {
                continue;
            }
            String normalized = deviceId.trim();
            if (!result.contains(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    private String sha256(MultipartFile file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        int len;
        try (InputStream inputStream = file.getInputStream()) {
            while ((len = inputStream.read(buffer)) > 0) {
                digest.update(buffer, 0, len);
            }
        }
        byte[] hash = digest.digest();
        StringBuilder builder = new StringBuilder();
        for (byte b : hash) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private String buildDownloadUrl(String objectName) {
        return normalizeDownloadBaseUrl() + "/upgrade/packages/{packageId}/download";
    }

    private void refreshDownloadUrl(RemoteUpgradePackage pkg) {
        if (pkg == null || pkg.getId() == null) {
            return;
        }
        pkg.setDownloadUrl(normalizeDownloadBaseUrl() + "/upgrade/packages/" + pkg.getId() + "/download");
    }

    private String normalizeDownloadBaseUrl() {
        if (!StringUtils.hasText(upgradeDownloadBaseUrl)) {
            throw new IllegalStateException("upgrade.download-base-url未配置，无法生成设备可访问的升级包下载地址");
        }
        String endpoint = upgradeDownloadBaseUrl.trim();
        while (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint;
    }

    private String sanitizeFilename(String filename) {
        String name = StringUtils.hasText(filename) ? filename : "package.bin";
        return name.replace("\\", "_").replace("/", "_").replace("..", "_");
    }

    private Map<String, Object> deviceResult(RemoteUpgradeTaskDevice device, boolean success, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", message);
        result.put("targetDeviceId", device.getTargetDeviceId());
        result.put("status", device.getStatus());
        result.put("mqttMessageId", device.getMqttMessageId());
        return result;
    }

    @Override
    public DeviceVersionsResponseDTO listDeviceVersions(String targetDeviceId) {
        if (!StringUtils.hasText(targetDeviceId)) {
            throw new IllegalArgumentException("targetDeviceId不能为空");
        }

        List<Map<String, Object>> rows = taskDeviceMapper.selectDeviceVersions(targetDeviceId.trim());
        List<DeviceVersionRecordDTO> records = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                DeviceVersionRecordDTO record = new DeviceVersionRecordDTO();
                record.setVersion(getStr(row, "version"));
                record.setStatus(getStr(row, "status"));
                record.setPackageId(getLong(row, "packageId"));
                record.setPackageName(getStr(row, "packageName"));
                record.setTaskId(getLong(row, "taskId"));
                record.setTaskName(getStr(row, "taskName"));
                record.setPackageType(getStr(row, "packageType"));
                record.setFileSize(getLong(row, "fileSize"));
                record.setSha256(getStr(row, "sha256"));
                record.setInstalledAt(getDateTime(row, "installedAt"));
                record.setDurationText(getStr(row, "durationText"));
                record.setOperator(getStr(row, "operator"));
                record.setRollbackable(Boolean.TRUE.equals(row.get("rollbackable")));
                record.setRemark(getStr(row, "remark"));
                records.add(record);
            }
        }

        String currentVersion = null;
        for (DeviceVersionRecordDTO r : records) {
            if ("SUCCESS".equals(r.getStatus())) {
                currentVersion = r.getVersion();
                break;
            }
        }

        DeviceVersionsResponseDTO response = new DeviceVersionsResponseDTO();
        response.setTargetDeviceId(targetDeviceId);
        response.setCurrentVersion(currentVersion);
        response.setTotal(records.size());
        response.setRecords(records);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RemoteUpgradeTaskDevice importDeviceVersion(String targetDeviceId, DeviceVersionImportDTO dto) {
        if (!StringUtils.hasText(targetDeviceId)) {
            throw new IllegalArgumentException("targetDeviceId不能为空");
        }

        RemoteUpgradePackage pkg = null;
        if (dto.getPackageId() != null) {
            pkg = packageMapper.selectById(dto.getPackageId());
            if (pkg == null) {
                throw new IllegalArgumentException("升级包不存在: " + dto.getPackageId());
            }
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime installedAt = dto.getInstalledAt() != null ? dto.getInstalledAt() : now;

        RemoteUpgradeTask task = new RemoteUpgradeTask();
        task.setTaskCode("IMPORT-" + CODE_TIME_FORMAT.format(now) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        task.setTaskName(StringUtils.hasText(dto.getOperator()) ? dto.getOperator() + " 的历史版本补录" : "历史版本补录");
        task.setPackageId(pkg != null ? pkg.getId() : null);
        task.setPackageCode(pkg != null ? pkg.getPackageCode() : null);
        task.setPackageType(pkg != null ? pkg.getPackageType() : null);
        task.setTargetVersion(StringUtils.hasText(dto.getVersion()) ? dto.getVersion()
                : (pkg != null ? pkg.getTargetVersion() : null));
        task.setStatus(RemoteUpgradeConstants.TASK_STATUS_SUCCESS);
        task.setTotalCount(1);
        task.setSuccessCount(1);
        task.setFailedCount(0);
        task.setTimeoutCount(0);
        task.setRollbackEnabled(false);
        task.setTimeoutSeconds(0);
        task.setOperator(dto.getOperator());
        task.setRemark(dto.getRemark());
        task.setStartTime(installedAt);
        task.setFinishTime(installedAt);
        task.setCreateTime(now);
        task.setUpdateTime(now);
        taskMapper.insert(task);

        RemoteUpgradeTaskDevice item = new RemoteUpgradeTaskDevice();
        item.setTaskId(task.getId());
        item.setTaskCode(task.getTaskCode());
        item.setTargetDeviceId(targetDeviceId);
        item.setDeviceType(pkg != null ? pkg.getDeviceType() : null);
        item.setCurrentVersion(null);
        item.setTargetVersion(task.getTargetVersion());
        item.setStatus(RemoteUpgradeConstants.DEVICE_STATUS_SUCCESS);
        item.setStage("SUCCESS");
        item.setProgress(100);
        item.setCommandTime(installedAt);
        item.setAckTime(installedAt);
        item.setFinishTime(installedAt);
        item.setCreateTime(now);
        item.setUpdateTime(now);
        taskDeviceMapper.insert(item);

        return item;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> rollbackToVersion(String targetDeviceId, RollbackDTO dto) {
        if (!StringUtils.hasText(targetDeviceId)) {
            throw new IllegalArgumentException("targetDeviceId不能为空");
        }

        RemoteUpgradePackage pkg = packageMapper.selectById(dto.getPackageId());
        if (pkg == null) {
            throw new IllegalArgumentException("升级包不存在: " + dto.getPackageId());
        }

        DeviceVersionsResponseDTO versions = listDeviceVersions(targetDeviceId);
        Map<Long, Boolean> rollbackableMap = new HashMap<>();
        if (versions.getRecords() != null) {
            for (DeviceVersionRecordDTO r : versions.getRecords()) {
                if (r.getTaskId() != null && r.getRollbackable() != null) {
                    rollbackableMap.put(r.getTaskId(), r.getRollbackable());
                }
            }
        }

        List<Map<String, Object>> historyRows = taskDeviceMapper.selectDeviceVersions(targetDeviceId);
        if (historyRows != null) {
            for (Map<String, Object> row : historyRows) {
                Long taskId = getLong(row, "taskId");
                if (taskId == null) continue;
                RemoteUpgradeTask histTask = taskMapper.selectById(taskId);
                if (histTask != null && Boolean.TRUE.equals(histTask.getRollbackEnabled())) {
                    if (Boolean.FALSE.equals(rollbackableMap.get(taskId))) {
                        rollbackableMap.put(taskId, true);
                    }
                }
            }
        }

        RemoteUpgradeTask latestTask = null;
        if (!historyRows.isEmpty()) {
            Long latestTaskId = getLong(historyRows.get(0), "taskId");
            if (latestTaskId != null) {
                latestTask = taskMapper.selectById(latestTaskId);
            }
        }
        if (latestTask != null && Boolean.FALSE.equals(latestTask.getRollbackEnabled())) {
            throw new IllegalStateException("该设备历史升级任务不允许回滚，无法执行回滚操作");
        }

        RemoteUpgradeTaskCreateDTO createDTO = new RemoteUpgradeTaskCreateDTO();
        createDTO.setPackageName(pkg.getPackageName() + " (回滚)");
        createDTO.setPackageId(pkg.getId());
        createDTO.setTargetDeviceIds(java.util.Collections.singletonList(targetDeviceId));
        createDTO.setRollbackEnabled(false);
        createDTO.setTimeoutSeconds(1800);
        createDTO.setOperator(dto.getOperator());
        createDTO.setRemark(StringUtils.hasText(dto.getRemark())
                ? dto.getRemark()
                : "回滚到版本 " + pkg.getTargetVersion());

        RemoteUpgradeTask task = createTask(createDTO);
        return startTask(task.getId(), false);
    }

    private String getStr(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDateTime getDateTime(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof LocalDateTime) return (LocalDateTime) value;
        if (value instanceof java.sql.Timestamp) return ((java.sql.Timestamp) value).toLocalDateTime();
        if (value instanceof java.util.Date) return new java.sql.Timestamp(((java.util.Date) value).getTime()).toLocalDateTime();
        return null;
    }
}
