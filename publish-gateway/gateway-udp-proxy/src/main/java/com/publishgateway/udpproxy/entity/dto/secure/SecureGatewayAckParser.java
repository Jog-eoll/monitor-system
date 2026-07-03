package com.publishgateway.udpproxy.entity.dto.secure;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 解密网关统一响应解析器
 * <p>
 * 兼容处理：
 * - step 字段同时兼容 "step" 和 "stepName"
 * - code 字段兼容数字和字符串
 * - unknown status 保留原始字符串并降级为 ERROR/FAILED
 * - 保留 mappedCapability、taskId、batchTaskId、orchestrationTaskId
 * </p>
 */
@Slf4j
public class SecureGatewayAckParser {

    public static SecureGatewayAck parse(String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            log.warn("[AckParser] 解密网关响应为空");
            SecureGatewayAck ack = new SecureGatewayAck();
            ack.setAccepted(false);
            ack.setStatus("ERROR");
            ack.setMessage("解密网关响应为空");
            return ack;
        }

        SecureGatewayAck ack = new SecureGatewayAck();

        try {
            JSONObject root = JSON.parseObject(responseBody);
            if (root == null) {
                log.warn("[AckParser] 解密网关响应 JSON 解析为 null");
                ack.setAccepted(false);
                ack.setStatus("BAD_JSON");
                ack.setMessage("解密网关响应 JSON 解析为 null");
                return ack;
            }

            // 外层 Result
            ack.setOuterCode(root.getInteger("code"));
            ack.setOuterMsg(root.getString("msg"));

            // 内层 data
            JSONObject data = root.getJSONObject("data");
            if (data == null) {
                if (ack.getOuterCode() != null && ack.getOuterCode() != 200) {
                    ack.setAccepted(false);
                    ack.setStatus("ERROR");
                    log.warn("[AckParser] 缺少 data: outerCode={}, outerMsg={}", ack.getOuterCode(), ack.getOuterMsg());
                    return ack;
                }
                log.warn("[AckParser] 解密网关响应缺少 data: {}", responseBody);
                ack.setAccepted(false);
                ack.setStatus("BAD_JSON");
                ack.setMessage("解密网关响应缺少 data");
                return ack;
            }

            ack.setAccepted(data.getBoolean("accepted"));

            // status: 优先用枚举名解析，解析失败时保留原始字符串并降级
            String rawStatus = data.getString("status");
            ack.setStatus(normalizeStatus(rawStatus));

            // code: 兼容数字和字符串
            ack.setCode(resolveCode(data, "code"));

            ack.setMessage(data.getString("message"));
            ack.setRequestId(data.getString("requestId"));
            ack.setTaskId(data.getString("taskId"));
            ack.setBatchTaskId(data.getString("batchTaskId"));
            ack.setOrchestrationTaskId(data.getString("orchestrationTaskId"));
            ack.setMappedCapability(data.getString("mappedCapability"));

            // steps: 同时兼容 step 和 stepName
            JSONArray stepsArr = data.getJSONArray("steps");
            if (stepsArr != null && !stepsArr.isEmpty()) {
                List<SecureGatewayAck.StepInfo> steps = new ArrayList<>();
                for (int i = 0; i < stepsArr.size(); i++) {
                    JSONObject stepObj = stepsArr.getJSONObject(i);
                    if (stepObj != null) {
                        SecureGatewayAck.StepInfo step = new SecureGatewayAck.StepInfo();

                        String stepName = stepObj.getString("stepName");
                        String stepField = stepObj.getString("step");
                        if (stepName != null && !stepName.isEmpty()) {
                            step.setStep(stepName);
                            step.setStepName(stepName);
                        } else if (stepField != null && !stepField.isEmpty()) {
                            step.setStep(stepField);
                            step.setStepName(stepField);
                        }

                        step.setStatus(normalizeStatus(stepObj.getString("status")));
                        step.setMessage(stepObj.getString("message"));
                        steps.add(step);
                    }
                }
                ack.setSteps(steps);
            }

            return ack;

        } catch (Exception e) {
            log.error("[AckParser] 解密网关响应解析失败: {}", e.getMessage(), e);
            SecureGatewayAck fallback = new SecureGatewayAck();
            fallback.setAccepted(false);
            fallback.setStatus("BAD_JSON");
            fallback.setMessage("解密网关响应解析失败: " + e.getMessage());
            return fallback;
        }
    }

    /**
     * 规范化 status：若为已知枚举则保留原样，未知时保留原始字符串并按失败降级
     */
    private static String normalizeStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.trim().isEmpty()) {
            return null;
        }
        SecureStatusCode statusCode = SecureStatusCode.fromString(rawStatus);
        if (statusCode != null) {
            return statusCode.name();
        }
        log.warn("[AckParser] 未知 status: {}, 保留原值并按失败降级", rawStatus);
        return rawStatus;
    }

    /**
     * 兼容数字和字符串类型的 code
     */
    private static String resolveCode(JSONObject obj, String field) {
        Object val = obj.get(field);
        if (val == null) {
            return null;
        }
        if (val instanceof String) {
            return (String) val;
        }
        return String.valueOf(val);
    }
}
