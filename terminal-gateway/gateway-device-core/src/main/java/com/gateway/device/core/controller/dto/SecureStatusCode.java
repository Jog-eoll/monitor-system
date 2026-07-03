package com.gateway.device.core.controller.dto;

/**
 * 密文指令统一状态码 —— 发布包与控制指令共用。
 * <p>
 * 该枚举固化了加密网关与解密网关之间 JSON 通信的响应语义，确保
 * 发布网关只依赖稳定字段（{@code status} / {@code code} / {@code message}）即可判读结果，
 * 不会因为发布与控制两条链路返回结构不同导致误判。
 * </p>
 *
 * <p>取值约定：</p>
 * <ul>
 *   <li>{@link #ACCEPTED} —— 解密 + 解析 + 编排启动成功，后续可按 batchTaskId / orchestrationTaskId 查询。</li>
 *   <li>{@link #REJECTED} —— 业务校验未通过（目标缺失、指令不支持、参数非法等）。</li>
 *   <li>{@link #DECRYPT_FAILED} —— 密文解密失败。</li>
 *   <li>{@link #BAD_JSON} —— 解密后的明文不是合法 JSON 或不符合信封结构。</li>
 *   <li>{@link #VALIDATION_FAILED} —— 信封字段校验失败（任务 ID 为空、target 缺失、文件列表为空等）。</li>
 *   <li>{@link #TARGET_NOT_FOUND} —— 设备选择器未匹配到任何目标设备。</li>
 *   <li>{@link #UNSUPPORTED_CAPABILITY} —— 外部 command 无法映射到可执行能力。</li>
 *   <li>{@link #DUPLICATE_REQUEST} —— 命中幂等缓存，重复投递被忽略并回放原响应摘要。</li>
 *   <li>{@link #ERROR} —— 未预期的系统异常。</li>
 *   <li>{@link #FAILED} —— 编排过程中存在失败步骤（发布包专用）。</li>
 * </ul>
 */
public enum SecureStatusCode {

    ACCEPTED("ACCEPTED", 200, "已接受"),
    REJECTED("REJECTED", 400, "被拒绝"),
    DECRYPT_FAILED("DECRYPT_FAILED", 400, "解密失败"),
    BAD_JSON("BAD_JSON", 400, "JSON 解析失败"),
    VALIDATION_FAILED("VALIDATION_FAILED", 400, "参数校验失败"),
    TARGET_NOT_FOUND("TARGET_NOT_FOUND", 404, "未找到目标设备"),
    UNSUPPORTED_CAPABILITY("UNSUPPORTED_CAPABILITY", 400, "不支持的能力"),
    DUPLICATE_REQUEST("DUPLICATE_REQUEST", 409, "重复请求"),
    ERROR("ERROR", 500, "系统异常"),
    FAILED("FAILED", 500, "执行失败");

    private final String status;
    private final int code;
    private final String defaultLabel;

    SecureStatusCode(String status, int code, String defaultLabel) {
        this.status = status;
        this.code = code;
        this.defaultLabel = defaultLabel;
    }

    public String status() {
        return status;
    }

    public int code() {
        return code;
    }

    public String defaultLabel() {
        return defaultLabel;
    }

    /**
     * 是否表示请求已被接受。
     */
    public boolean isAccepted() {
        return this == ACCEPTED;
    }

    /**
     * 是否属于失败终态（发布网关据此判断是否抛出/告警）。
     */
    public boolean isFailure() {
        return this != ACCEPTED;
    }
}
