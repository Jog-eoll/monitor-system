package com.gateway.device.core.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.common.service.CryptoService;
import com.gateway.device.core.controller.dto.SecureCommandResponse;
import com.gateway.device.core.controller.dto.SecureStatusCode;
import com.gateway.device.core.controller.dto.StandardizedPublishPackage;
import com.gateway.device.core.controller.dto.StandardizedPublishPackage.FileEntry;
import com.gateway.device.core.controller.dto.StandardizedPublishPackage.PublishOptions;
import com.gateway.device.core.selector.DeviceSelectorResolver;
import com.gateway.device.core.service.BatchCommandService;
import com.gateway.device.core.service.PublishTaskRegistry;
import com.gateway.device.core.service.SecureCommandIdempotencyCache;
import com.gateway.device.protocol.base.jetfileii.standard.command.FileType;
import com.gateway.device.protocol.base.jetfileii.standard.command.Partition;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.expand.JetFileIICapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.DeviceVendor;
import com.gateway.device.protocol.model.BatchCommandRequest;
import com.gateway.device.protocol.model.BatchTask;
import com.gateway.device.protocol.model.DeviceCommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.DeviceSelector;
import com.gateway.device.protocol.model.params.EmptyParams;
import com.gateway.device.protocol.model.params.MediaUploadParams;
import com.gateway.device.protocol.model.params.PlaylistSetParams;
import com.gateway.device.protocol.model.params.TextUploadParams;
import com.gateway.device.protocol.model.params.depend.CommandParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 发布包编排器 —— 解密标准发布包，并按 V14 设备能力模型顺序下发。
 */
@Slf4j
@Service
public class PublishPackageOrchestrator {

    private static final long STEP_TIMEOUT_SECONDS = 75L;

    private final CryptoService cryptoService;
    private final BatchCommandService batchCommandService;
    private final DeviceSelectorResolver selectorResolver;
    private final SecureEnvelopeParser envelopeParser;
    private final SecureCommandIdempotencyCache idempotencyCache;
    private final PublishTaskRegistry publishTaskRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PublishPackageOrchestrator(CryptoService cryptoService,
                                      BatchCommandService batchCommandService,
                                      DeviceSelectorResolver selectorResolver,
                                      SecureEnvelopeParser envelopeParser,
                                      SecureCommandIdempotencyCache idempotencyCache,
                                      PublishTaskRegistry publishTaskRegistry) {
        this.cryptoService = cryptoService;
        this.batchCommandService = batchCommandService;
        this.selectorResolver = selectorResolver;
        this.envelopeParser = envelopeParser;
        this.idempotencyCache = idempotencyCache;
        this.publishTaskRegistry = publishTaskRegistry;
    }

    public SecureCommandResponse orchestrate(String requestId, String deliveryTaskId, byte[] encryptedData) {
        String orchestrationTaskId = firstNonBlank(requestId,
                "ORCH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));

        // ── 幂等：优先按 requestId/deliveryTaskId 回放 ──
        String idempotencyKey = resolveIdempotencyKey(requestId, deliveryTaskId);
        java.util.Optional<SecureCommandResponse> cached =
                idempotencyCache.findPublish(idempotencyKey);
        if (cached.isPresent()) {
            SecureCommandResponse replay = cached.get();
            log.info("[发布编排] 幂等命中: key={}, orchestrationTaskId={}, deliveryTaskId={}",
                    idempotencyKey, replay.getOrchestrationTaskId(), deliveryTaskId);
            replay.setRequestId(requestId);
            return replay;
        }

        // ── 第一步: 解密 ──
        byte[] decrypted;
        try {
            decrypted = cryptoService.decrypt(encryptedData);
        } catch (Exception e) {
            log.error("[发布编排] 解密失败: deliveryTaskId={}, error={}", deliveryTaskId, e.getMessage(), e);
            return attachRequestId(SecureCommandResponse.rejected(
                    deliveryTaskId, SecureStatusCode.DECRYPT_FAILED, "密文解密失败: " + e.getMessage()), requestId);
        }

        String json = new String(decrypted, StandardCharsets.UTF_8);
        log.info("[发布编排] 解密成功: orchestrationTaskId={}, deliveryTaskId={}, requestId={}, decryptedLength={}",
                orchestrationTaskId, deliveryTaskId, requestId, json.length());

        // ── 第二步: 解析（兼容新信封与旧扁平 DTO）──
        StandardizedPublishPackage pkg;
        boolean usedEnvelope = false;
        try {
            SecureEnvelopeParser.ParseOutcome<StandardizedPublishPackage> outcome =
                    envelopeParser.parsePublish(json);
            pkg = outcome.value();
            usedEnvelope = outcome.isEnvelope();
        } catch (Exception e) {
            log.error("[发布编排] JSON 解析失败: deliveryTaskId={}, error={}", deliveryTaskId, e.getMessage(), e);
            return attachRequestId(SecureCommandResponse.rejected(
                    deliveryTaskId, SecureStatusCode.BAD_JSON, "JSON 解析失败: " + e.getMessage()), requestId);
        }

        // 信封内的 deliveryTaskId 优先回填，便于后续追踪
        if (StringUtils.isNotBlank(pkg.getDeliveryTaskId()) && StringUtils.isBlank(deliveryTaskId)) {
            deliveryTaskId = pkg.getDeliveryTaskId();
        }

        log.info("[发布编排] 解析完成: orchestrationTaskId={}, envelope={}, files={}, target={}",
                orchestrationTaskId, usedEnvelope,
                pkg.getFiles() != null ? pkg.getFiles().size() : 0,
                pkg.getTarget() != null ? pkg.getTarget().getDeviceId() : null);

        // ── 第三步: 参数校验 ──
        SecureCommandResponse invalid = validate(deliveryTaskId, pkg);
        if (invalid != null) {
            return attachRequestId(invalid, requestId);
        }

        DeviceContext device;
        try {
            List<DeviceContext> devices = selectorResolver.resolve(buildSelector(pkg.getTarget()));
            if (devices == null || devices.isEmpty()) {
                return attachRequestId(SecureCommandResponse.rejected(
                        deliveryTaskId, SecureStatusCode.TARGET_NOT_FOUND, "未找到匹配的目标设备"), requestId);
            }
            device = devices.get(0);
        } catch (Exception e) {
            log.error("[发布编排] 设备解析异常: {}", e.getMessage(), e);
            return attachRequestId(SecureCommandResponse.rejected(
                    deliveryTaskId, SecureStatusCode.ERROR, "设备解析异常: " + e.getMessage()), requestId);
        }

        PublishOptions options = pkg.getOptions() != null ? pkg.getOptions() : new PublishOptions();
        List<SecureCommandResponse.StepResult> steps = new ArrayList<>();
        List<String> uploadedPaths = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        if (options.isClearBeforePublish()) {
            SecureCommandResponse.StepResult clearResult =
                    executeClearStep(orchestrationTaskId, device);
            steps.add(clearResult);
            if (!"SUCCESS".equals(clearResult.getStatus())) {
                log.warn("[发布编排] 清理步骤失败，继续发布: device={}, message={}",
                        device.getDeviceId(), clearResult.getMessage());
            }
        }

        List<FileEntry> files = new ArrayList<>(pkg.getFiles());
        files.sort(Comparator.comparingInt(f -> f.getOrderNo() != null ? f.getOrderNo() : 0));

        for (FileEntry file : files) {
            SecureCommandResponse.StepResult uploadResult =
                    executeUploadStep(orchestrationTaskId, device, file);
            steps.add(uploadResult);
            if ("SUCCESS".equals(uploadResult.getStatus())) {
                String path = firstNonBlank(uploadResult.getDevicePath(), resolveDevicePath(device, file));
                uploadResult.setDevicePath(path);
                if (StringUtils.isNotBlank(path)) {
                    uploadedPaths.add(path);
                }
            } else {
                failures.add(file.getFileName());
            }
        }

        if (!failures.isEmpty()) {
            SecureCommandResponse response = SecureCommandResponse.failed(
                    deliveryTaskId, orchestrationTaskId, "文件上传失败: " + failures);
            response.setSteps(steps);
            publishTaskRegistry.record(response);
            return attachRequestId(response, requestId);
        }

        SecureCommandResponse.StepResult playlistResult =
                executePlaylistStep(orchestrationTaskId, device, pkg, uploadedPaths, options);
        if (playlistResult != null) {
            steps.add(playlistResult);
            if (!"SUCCESS".equals(playlistResult.getStatus())) {
                SecureCommandResponse response = SecureCommandResponse.failed(
                        deliveryTaskId, orchestrationTaskId,
                        "设置播放列表失败: " + playlistResult.getMessage());
                response.setSteps(steps);
                publishTaskRegistry.record(response);
                return attachRequestId(response, requestId);
            }
        }

        SecureCommandResponse response = SecureCommandResponse.accepted(deliveryTaskId, orchestrationTaskId);
        response.setSteps(steps);
        log.info("[发布编排] 发布完成: orchestrationTaskId={}, deliveryTaskId={}, device={}, steps={}",
                orchestrationTaskId, deliveryTaskId, device.getDeviceId(), steps.size());
        publishTaskRegistry.record(response);
        idempotencyCache.putPublish(idempotencyKey, response);
        return attachRequestId(response, requestId);
    }

    private SecureCommandResponse validate(String deliveryTaskId, StandardizedPublishPackage pkg) {
        if (pkg == null) {
            return SecureCommandResponse.rejected(deliveryTaskId, SecureStatusCode.VALIDATION_FAILED, "发布包为空");
        }
        if (pkg.getTarget() == null) {
            return SecureCommandResponse.rejected(deliveryTaskId, SecureStatusCode.VALIDATION_FAILED, "目标设备信息不能为空");
        }
        if (pkg.getFiles() == null || pkg.getFiles().isEmpty()) {
            return SecureCommandResponse.rejected(deliveryTaskId, SecureStatusCode.VALIDATION_FAILED, "文件列表不能为空");
        }
        return null;
    }

    private SecureCommandResponse.StepResult executeClearStep(String orchestrationTaskId,
                                                              DeviceContext device) {
        if (supports(device, CommonDeviceCapability.MEDIA_CLEAR)) {
            return executeStep(orchestrationTaskId, "CLEAR_MEDIA", device,
                    CommonDeviceCapability.MEDIA_CLEAR, EmptyParams.INSTANCE, null);
        }
        if (supports(device, CommonDeviceCapability.PLAYLIST_CLEAR)) {
            return executeStep(orchestrationTaskId, "CLEAR_PLAYLIST", device,
                    CommonDeviceCapability.PLAYLIST_CLEAR, EmptyParams.INSTANCE, null);
        }
        SecureCommandResponse.StepResult result = new SecureCommandResponse.StepResult();
        result.setStep("CLEAR");
        result.setStatus("FAILED");
        result.setMessage("目标设备不支持清理能力");
        return result;
    }

    private SecureCommandResponse.StepResult executeUploadStep(String orchestrationTaskId,
                                                               DeviceContext device,
                                                               FileEntry file) {
        SecureCommandResponse.StepResult step = new SecureCommandResponse.StepResult();
        step.setStep("FILE_UPLOAD");
        step.setFileOrderNo(file.getOrderNo());
        step.setFileName(file.getFileName());

        DeviceCapability<?> capability = resolveUploadCapability(file.getFileType());
        if (capability == null) {
            step.setStatus("FAILED");
            step.setMessage("不支持的文件类型: " + file.getFileType());
            return step;
        }

        CommandParams params;
        try {
            params = buildUploadParams(capability, file);
        } catch (Exception e) {
            step.setStatus("FAILED");
            step.setMessage("构建上传参数失败: " + e.getMessage());
            return step;
        }

        return executeStep(orchestrationTaskId, "FILE_UPLOAD", device, capability, params, file);
    }

    private SecureCommandResponse.StepResult executePlaylistStep(String orchestrationTaskId,
                                                                 DeviceContext device,
                                                                 StandardizedPublishPackage pkg,
                                                                 List<String> uploadedPaths,
                                                                 PublishOptions options) {
        if (!supports(device, CommonDeviceCapability.PLAYLIST_SET)) {
            return null;
        }

        PlaylistSetParams params;
        if (DeviceVendor.JET_FILE_II_STANDARD.equals(device.getVendor())) {
            if (uploadedPaths.isEmpty()) {
                SecureCommandResponse.StepResult result = new SecureCommandResponse.StepResult();
                result.setStep("PLAYLIST_SET");
                result.setStatus("FAILED");
                result.setMessage("设备侧播放路径为空");
                return result;
            }
            params = PlaylistSetParams.builder()
                    .paths(uploadedPaths)
                    .checkExistence(options.isCheckExistence())
                    .build();
        } else {
            String identifier = pkg.getPlaylist() != null ? pkg.getPlaylist().getPlaylistId() : null;
            String name = firstFileName(pkg.getFiles());
            if (StringUtils.isBlank(identifier) && StringUtils.isBlank(name)) {
                return null;
            }
            params = PlaylistSetParams.builder()
                    .identifier(identifier)
                    .name(name)
                    .build();
        }

        return executeStep(orchestrationTaskId, "PLAYLIST_SET", device,
                CommonDeviceCapability.PLAYLIST_SET, params, null);
    }

    private SecureCommandResponse.StepResult executeStep(String orchestrationTaskId,
                                                         String stepName,
                                                         DeviceContext device,
                                                         DeviceCapability<?> capability,
                                                         CommandParams params,
                                                         FileEntry file) {
        SecureCommandResponse.StepResult step = new SecureCommandResponse.StepResult();
        step.setStep(stepName);
        if (file != null) {
            step.setFileOrderNo(file.getOrderNo());
            step.setFileName(file.getFileName());
        }

        String requestId = buildStepRequestId(orchestrationTaskId, stepName, file);
        step.setBatchTaskId(requestId);

        try {
            BatchCommandRequest request = BatchCommandRequest.builder()
                    .requestId(requestId)
                    .capability(capability)
                    .selector(singleDeviceSelector(device, capability))
                    .params(params)
                    .build();
            BatchTask task = batchCommandService.submitAsync(request)
                    .get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            fillStepResult(step, task);
            if (file != null && "SUCCESS".equals(step.getStatus())) {
                step.setDevicePath(extractDevicePath(task));
            }
        } catch (Exception e) {
            step.setStatus("ERROR");
            step.setMessage(e.getMessage());
            log.error("[发布编排] 步骤异常: step={}, device={}, capability={}, error={}",
                    stepName, device.getDeviceId(), capability.name(), e.getMessage(), e);
        }
        return step;
    }

    private void fillStepResult(SecureCommandResponse.StepResult step, BatchTask task) {
        step.setBatchTaskId(task.getTaskId());
        if (task.getTotal() <= 0) {
            step.setStatus("FAILED");
            step.setMessage("未找到支持该能力的目标设备");
            return;
        }
        switch (task.getStatus()) {
            case SUCCESS:
                step.setStatus("SUCCESS");
                break;
            case TIMEOUT:
                step.setStatus("TIMEOUT");
                break;
            default:
                step.setStatus("FAILED");
                break;
        }
        step.setMessage(firstResultMessage(task));
    }

    private DeviceSelector buildSelector(StandardizedPublishPackage.TargetRef target) {
        DeviceSelector.DeviceSelectorBuilder builder = DeviceSelector.builder();
        builder.onlineOnly(true);

        if (StringUtils.isNotBlank(target.getIp())) {
            builder.ips(Collections.singleton(target.getIp().trim()));
        } else if (StringUtils.isNotBlank(target.getDeviceId())) {
            builder.deviceIds(Collections.singleton(target.getDeviceId().trim()));
        }

        DeviceVendor vendor = resolveVendor(target.getVendorHint());
        if (vendor != null) {
            builder.vendors(Collections.singleton(vendor));
        }
        return builder.build();
    }

    private DeviceSelector singleDeviceSelector(DeviceContext device, DeviceCapability<?> capability) {
        return DeviceSelector.builder()
                .deviceIds(Collections.singleton(device.getDeviceId()))
                .vendors(Collections.singleton(device.getVendor()))
                .requiredCapabilities(Collections.singleton(capability))
                .onlineOnly(true)
                .build();
    }

    private CommandParams buildUploadParams(DeviceCapability<?> capability, FileEntry file) {
        byte[] data = decodeContent(file);
        if (capability == CommonDeviceCapability.TEXT_UPLOAD) {
            return TextUploadParams.builder()
                    .text(new String(data, StandardCharsets.UTF_8))
                    .data(data)
                    .partition(Partition.D)
                    .build();
        }
        return MediaUploadParams.builder()
                .data(data)
                .fileName(resolveFileName(file))
                .partition(Partition.D)
                .chunkSize(1024)
                .build();
    }

    private byte[] decodeContent(FileEntry file) {
        if (StringUtils.isBlank(file.getContentBase64())) {
            throw new IllegalArgumentException("contentBase64 不能为空");
        }
        return Base64.getDecoder().decode(file.getContentBase64());
    }

    private DeviceCapability<?> resolveUploadCapability(String fileType) {
        if (StringUtils.isBlank(fileType)) {
            return null;
        }
        switch (fileType.trim().toUpperCase(Locale.ROOT)) {
            case "IMAGE":
            case "JPG":
            case "JPEG":
            case "PNG":
            case "BMP":
                return CommonDeviceCapability.IMAGE_UPLOAD;
            case "VIDEO":
            case "MP4":
            case "FLV":
            case "AVI":
                return CommonDeviceCapability.VIDEO_UPLOAD;
            case "TEXT":
            case "TXT":
                return CommonDeviceCapability.TEXT_UPLOAD;
            case "NMG":
                return JetFileIICapability.NMG_FILE_UPLOAD;
            case "PMG":
                return JetFileIICapability.PMG_FILE_UPLOAD;
            case "QST":
                return JetFileIICapability.QST_FILE_UPLOAD;
            default:
                return null;
        }
    }

    private String resolveDevicePath(DeviceContext device, FileEntry file) {
        if (!DeviceVendor.JET_FILE_II_STANDARD.equals(device.getVendor())) {
            return null;
        }
        FileType fileType = resolveDeviceFileType(file.getFileType());
        return fileType == null ? null : fileType.resolvePath(Partition.D, resolveFileName(file));
    }

    private FileType resolveDeviceFileType(String fileType) {
        if (StringUtils.isBlank(fileType)) {
            return null;
        }
        switch (fileType.trim().toUpperCase(Locale.ROOT)) {
            case "IMAGE":
            case "JPG":
            case "JPEG":
            case "PNG":
            case "BMP":
                return FileType.PICTURE;
            case "VIDEO":
            case "MP4":
            case "FLV":
            case "AVI":
                return FileType.FLW;
            case "TEXT":
            case "TXT":
            case "NMG":
                return FileType.TEXT;
            case "PMG":
                return FileType.ARRAY_PICTURE;
            case "QST":
                return FileType.ARRAY_QST;
            default:
                return null;
        }
    }

    private String resolveFileName(FileEntry file) {
        String fileName = firstNonBlank(file.getFileName());
        if (StringUtils.isNotBlank(fileName)) {
            return fileName;
        }
        int orderNo = file.getOrderNo() != null ? file.getOrderNo() : 0;
        return "file-" + orderNo + defaultExtension(file.getFileType());
    }

    private String defaultExtension(String fileType) {
        if (StringUtils.isBlank(fileType)) {
            return ".bin";
        }
        switch (fileType.trim().toUpperCase(Locale.ROOT)) {
            case "IMAGE":
                return ".jpg";
            case "VIDEO":
                return ".mp4";
            case "TEXT":
                return ".txt";
            default:
                return "." + fileType.trim().toLowerCase(Locale.ROOT);
        }
    }

    private String extractDevicePath(BatchTask task) {
        for (DeviceCommandResult result : task.getResults()) {
            Object data = result.getData();
            if (data instanceof Map<?, ?>) {
                Map<?, ?> map = (Map<?, ?>) data;
                for (String key : new String[]{"playlist", "deviceFilePath", "remotePath", "path"}) {
                    Object value = map.get(key);
                    if (value != null && StringUtils.isNotBlank(value.toString())) {
                        return value.toString();
                    }
                }
            }
            if (data instanceof String && StringUtils.isNotBlank((String) data)) {
                return (String) data;
            }
        }
        return null;
    }

    private String firstResultMessage(BatchTask task) {
        for (DeviceCommandResult result : task.getResults()) {
            if (StringUtils.isNotBlank(result.getMessage())) {
                return result.getMessage();
            }
        }
        return task.getStatus().name();
    }

    private boolean supports(DeviceContext device, DeviceCapability<?> capability) {
        Set<DeviceCapability<?>> capabilities = device.getCapabilities();
        return capabilities != null && capabilities.contains(capability);
    }

    private String buildStepRequestId(String orchestrationTaskId, String stepName, FileEntry file) {
        StringBuilder builder = new StringBuilder(orchestrationTaskId).append("-").append(stepName);
        if (file != null) {
            if (file.getOrderNo() != null) {
                builder.append("-").append(file.getOrderNo());
            } else {
                builder.append("-").append(Math.abs(resolveFileName(file).hashCode()));
            }
        }
        return builder.toString();
    }

    private String firstFileName(List<FileEntry> files) {
        if (files == null) {
            return null;
        }
        for (FileEntry file : files) {
            if (StringUtils.isNotBlank(file.getFileName())) {
                return file.getFileName();
            }
        }
        return null;
    }

    private DeviceVendor resolveVendor(String vendorHint) {
        if (StringUtils.isBlank(vendorHint)) {
            return null;
        }
        String normalized = vendorHint.trim().toUpperCase(Locale.ROOT)
                .replace("-", "_")
                .replace(" ", "_");
        if ("QINGSONG".equals(normalized)
                || "QING_SONG".equals(normalized)
                || "JETFILEII".equals(normalized)
                || "JET_FILEII".equals(normalized)
                || "JET_FILE_II".equals(normalized)) {
            return DeviceVendor.JET_FILE_II_STANDARD;
        }
        if ("COLORLIGHT".equals(normalized) || "COLOR_LIGHT".equals(normalized)) {
            return DeviceVendor.COLOR_LIGHT_STANDARD;
        }
        try {
            return DeviceVendor.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * 回显 requestId 到响应。
     */
    private SecureCommandResponse attachRequestId(SecureCommandResponse response, String requestId) {
        if (response != null && StringUtils.isNotBlank(requestId)) {
            response.setRequestId(requestId);
        }
        return response;
    }

    /**
     * 幂等键：requestId 优先，deliveryTaskId 兜底。
     */
    private String resolveIdempotencyKey(String requestId, String deliveryTaskId) {
        return firstNonBlank(requestId, deliveryTaskId);
    }
}
