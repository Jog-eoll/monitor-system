package com.gateway.device.core.controller;

import com.gateway.common.Result;
import com.gateway.device.core.controller.dto.BatchCommandRequestDTO;
import com.gateway.device.core.service.BatchCommandService;
import com.gateway.device.core.service.CommandParamsMapper;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.model.BatchCommandRequest;
import com.gateway.device.protocol.model.BatchTask;
import com.gateway.device.protocol.model.DeviceSelector;
import com.gateway.device.protocol.model.params.depend.CommandParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 批量指令 HTTP 入口
 * <p>
 * 对外接收中文业务动作（action），如"设置播放列表"、"调节亮度"；
 * 内部通过 {@link ActionMapper} 翻译为 {@link DeviceCapability}，
 * 再构建 {@link BatchCommandRequest} 委托给 {@link BatchCommandService} 执行。
 * </p>
 *
 * <p>完整链路：
 * <pre>
 *   Sigma JSON { action: "设置播放列表" }
 *       ↓  本控制器
 *   ActionMapper → PLAYLIST_SET
 *       ↓  BatchCommandService
 *   JetFileII: DISPLAY / DISP_REPLAY_LIST (0x06/0x01)
 * </pre>
 * </p>
 *
 * <p>接口路径：{@code POST /api/command/submit}</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/command")
public class BatchCommandController {

    @Resource
    private BatchCommandService batchCommandService;

    @Resource
    private ActionMapper actionMapper;

    @Resource
    private TargetToSelectorMapper targetToSelectorMapper;

    @Resource
    private CommandParamsMapper commandParamsMapper;

    /**
     * 提交批量指令
     *
     * @param dto 标准化指令请求体（含 action 业务动作字段）
     * @return 包含 taskId 和执行状态的结果
     */
    @PostMapping("/submit")
    public Result<Map<String, Object>> submit(@RequestBody BatchCommandRequestDTO dto) {
        Result<Map<String, Object>> invalidResult = validate(dto);
        if (invalidResult != null) {
            return invalidResult;
        }

        log.info("==========================================");
        log.info("[解密网关] 收到批量指令请求");
        log.info("==========================================");
        log.info("requestId={}, action={}, target={}",
                dto.getRequestId(), dto.getAction(),
                dto.getTarget() != null ? dto.getTarget().getDeviceId() : "broadcast");

        // ── 1. 业务动作 → 内部 DeviceCapability ──
        DeviceCapability<?> capability = actionMapper.map(dto.getAction());
        if (capability == null) {
            log.error("指令提交失败: 未识别的业务动作 '{}'", dto.getAction());
            return Result.error("未识别的业务动作: " + dto.getAction());
        }

        // ── 2. 根据 action 自动填充控制参数默认值 ──
        Map<String, Object> params = dto.getParams();
        if (params == null) {
            params = new HashMap<>();
        } else {
            params = new HashMap<>(params);
        }
        try {
            mergeContentParams(dto, params);
        } catch (IllegalArgumentException e) {
            log.error("指令提交失败: requestId={}, content解析失败: {}",
                    dto.getRequestId(), e.getMessage());
            return Result.error("content解析失败: " + e.getMessage());
        }
        actionMapper.applyActionDefaults(dto.getAction(), params);
        CommandParams commandParams;
        try {
            commandParams = commandParamsMapper.map(capability, params);
        } catch (Exception e) {
            log.error("指令提交失败: requestId={}, params转换失败: {}",
                    dto.getRequestId(), e.getMessage(), e);
            return Result.error("params转换失败: " + e.getMessage());
        }

        // ── 3. 映射目标设备 → DeviceSelector ──
        DeviceSelector selector = targetToSelectorMapper.map(dto.getTarget());
        selector.setRequiredCapabilities(Collections.singleton(capability));

        // ── 4. 构建内部 BatchCommandRequest ──
        BatchCommandRequest request = BatchCommandRequest.builder()
                .requestId(dto.getRequestId())
                .capability(capability)
                .selector(selector)
                .params(commandParams)
                .build();

        // ── 5. 提交执行 ──
        try {
            BatchTask task = batchCommandService.submit(request);

            Map<String, Object> result = new HashMap<>();
            result.put("taskId", task.getTaskId());
            result.put("status", "SUBMITTED");
            result.put("action", dto.getAction());
            result.put("internalCapability", capability.name());

            log.info("指令已提交: taskId={}, action={}, capability={}",
                    task.getTaskId(), dto.getAction(), capability.name());
            return Result.success("指令已提交", result);
        } catch (Exception e) {
            log.error("指令提交异常: requestId={}, error={}", dto.getRequestId(), e.getMessage(), e);
            return Result.error("指令提交失败: " + e.getMessage());
        }
    }

    /**
     * 查询所有已注册的业务动作（供对接方参考）
     */
    @GetMapping("/actions")
    public Result<Set<String>> listActions() {
        return Result.success("支持的业务动作", actionMapper.listActions());
    }

    private Result<Map<String, Object>> validate(BatchCommandRequestDTO dto) {
        if (dto == null) {
            return Result.error("请求体不能为空");
        }
        if (dto.getRequestId() == null || dto.getRequestId().trim().isEmpty()) {
            return Result.error("requestId 不能为空");
        }
        if (dto.getAction() == null || dto.getAction().trim().isEmpty()) {
            return Result.error("action 不能为空");
        }
        return null;
    }

    /**
     * 将标准化 JSON 的 content 字段转换为现有 Handler 使用的 params。
     * <p>
     * 文件上传类 Handler 使用 {@code params.data(byte[])}，文本上传可使用
     * {@code params.text(String)}。显式传入的 params 优先级更高，方便内部调用方覆盖。
     * </p>
     */
    private void mergeContentParams(BatchCommandRequestDTO dto, Map<String, Object> params) {
        BatchCommandRequestDTO.ContentInfo content = dto.getContent();
        if (content == null) {
            return;
        }

        String fileName = trim(content.getFileName());
        putIfHasText(params, "fileName", fileName);
        putIfHasText(params, "fileType", content.getFileType());
        putIfHasText(params, "minioPath", content.getMinioPath());
        putIfHasText(params, "imageFormat", content.getImageFormat());
        if (!params.containsKey("remotePath") && !params.containsKey("label") && fileName != null) {
            params.put("label", fileName);
        }
        if (params.containsKey("label") && !params.containsKey("partition")) {
            params.put("partition", (int) 'D');
        }

        String data = content.getData();
        if (data == null || data.trim().isEmpty()) {
            return;
        }

        if (params.containsKey("data") || params.containsKey("text")) {
            return;
        }

        if (isTextAction(dto.getAction(), content.getFileType())) {
            params.put("text", data);
            return;
        }

        params.put("data", decodeBase64(data));
    }

    private void putIfHasText(Map<String, Object> params, String key, String value) {
        if (value != null && !value.trim().isEmpty() && !params.containsKey(key)) {
            params.put(key, value);
        }
    }

    private boolean isTextAction(String action, String fileType) {
        String normalizedAction = trim(action);
        if ("上传文字".equals(normalizedAction) || "上传文本播放文件".equals(normalizedAction)) {
            return true;
        }
        return "text".equalsIgnoreCase(trim(fileType));
    }

    private byte[] decodeBase64(String value) {
        String normalized = value.trim();
        try {
            return Base64.getDecoder().decode(normalized);
        } catch (IllegalArgumentException e) {
            try {
                return Base64.getMimeDecoder().decode(normalized);
            } catch (IllegalArgumentException ignored) {
                throw new IllegalArgumentException("content.data 不是合法 Base64 文件内容");
            }
        }
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
