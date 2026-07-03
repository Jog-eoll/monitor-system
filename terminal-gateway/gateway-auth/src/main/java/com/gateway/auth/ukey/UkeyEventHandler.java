package com.gateway.auth.ukey;

import com.gateway.auth.jna.VAuthSDKAdapter;
import com.gateway.auth.service.MonitorPlatformClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @Description: java类描述
 * @Author: zqr
 * @Date: 2026/4/23 16:58:43
 */
@Component
@Slf4j
public class UkeyEventHandler {

    @Resource
    private VAuthSDKAdapter vAuthSDKAdapter;

    @Resource
    private UkeyLifecycleManager lifecycleManager;


    @Resource
    private MonitorPlatformClient monitorPlatformClient;


    public void handleUkeyEvent(int eventType, String deviceName, String message) {
        if (eventType == 1) {
            onUkeyInserted(deviceName);
        } else if (eventType == 2) {
            onUkeyRemoved(deviceName);
        }
    }



    /**
     * 处理UKey插入事件
     */
    private void onUkeyInserted(String deviceName) {
        log.info("========================================");
        log.info("  UKey插入事件");
        log.info("  设备: {}", deviceName);
        log.info("========================================");

        try {

            // 1. 列出可用的UKey设备
//            String ukeyList = vAuthSDKAdapter.listUkeyInfos();
//            if (ukeyList == null || ukeyList.trim().equals("[]")) {
//                lifecycleManager.onError("未检测到UKey设备");
//                return;
//            }
//            log.info("检测到UKey设备列表: {}", ukeyList);

            // 2. 解析UKey信息
//            String ukeyPath = extractUkeyPath(ukeyList);
//            String certSerialNo = extractCertId(ukeyList);
//
//            if (ukeyPath == null || certSerialNo == null) {
//                lifecycleManager.onError("解析UKey信息失败");
//                return;
//            }

            // 状态机: IDLE -> UKEY_DETECTED
//            lifecycleManager.onUkeyDetected(ukeyPath, certSerialNo);
//            log.info("UKey已检测: path={}, certSerialNo={}", ukeyPath, certSerialNo);

            // 3. 向管控平台请求证书校验
//            String certificateContent = extractCertificateContent(ukeyList);
//            Map<String, Object> validateResult = monitorPlatformClient.validateCertificate(certSerialNo, certificateContent);

//            boolean valid = (boolean) validateResult.getOrDefault("valid", false);
//            if (!valid) {
//                String errorMsg = (String) validateResult.getOrDefault("message", "证书校验失败");
//                lifecycleManager.onError(errorMsg);
//                log.error("证书校验失败: {}", errorMsg);
//                return;
//            }

            // 状态机: UKEY_DETECTED -> UKEY_VALIDATED
            lifecycleManager.onUkeyValidated();
            log.info("证书校验通过");

            // 4. 发起双向认证
            lifecycleManager.onAuthenticating();

//            boolean authSuccess = clientAuthService.authenticateWithControlPlatform(ukeyPath);
//            if (!authSuccess) {
//                lifecycleManager.onError("双向认证失败");
//                log.error("双向认证失败");
//                return;
//            }

            // 状态机: AUTHENTICATING -> AUTHENTICATED
            lifecycleManager.onAuthenticated();
            log.info("双向认证成功");

            // 5. 通知管控平台认证成功
            String authToken = "AUTH-TOKEN-" + System.currentTimeMillis();
//            monitorPlatformClient.notifyClientAuthenticated(certSerialNo, authToken);

            // 6. 恢复链路规则：重新激活 ARP 绑定
//            try {
//                proxyRuleManager.reactivateRulesOnUkeyAuthenticated();
//                log.info("链路规则已重新激活，ARP 绑定已恢复");
//            } catch (Exception e) {
//                log.warn("恢复链路规则失败: {}", e.getMessage());
//            }

            log.info("========================================");
            log.info("  UKey认证流程完成");
            log.info("  状态: {}", lifecycleManager.getCurrentState());
            log.info("========================================");

        } catch (Exception e) {
            log.error("UKey认证流程异常", e);
            lifecycleManager.onError("认证流程异常: " + e.getMessage());
        }
    }



    private void onUkeyRemoved(String deviceName) {
        log.warn("========================================");
        log.warn("  UKey拔出事件: {}", deviceName);
        log.warn("========================================");

        String certSerialNo = lifecycleManager.getCurrentCertSerialNo();

        try {
            // 1. 如果有活跃通道，先停止转发通道
//            if (lifecycleManager.isChannelActive()) {
//                log.info("正在停止转发通道...");
//                try {
//                    Map<String, Object> stopResult = gatewayService.stopForwardChannel();
//                    log.info("停止转发通道结果: {}", stopResult);
//                } catch (Exception e) {
//                    log.error("停止转发通道失败", e);
//                }
//            }

            // 2. 禁用本地所有链路规则 + 解绑 ARP（UKey 不在 = 不允许数据发送）
//            try {
//                proxyRuleManager.disableAllRulesOnUkeyRemoval();
//                // 检查 ARP 是否真正解除
//                if (arpBindService.getStatus().isBound()) {
//                    log.warn("⚠ 本地链路规则已禁用，但 ARP 绑定解除失败（可能未以管理员身份运行）");
//                } else {
//                    log.info("本地链路规则已全部禁用，ARP 绑定已解除");
//                }
//            } catch (Exception e) {
//                log.error("禁用链路规则失败", e);
//            }
            // 3. 通知管控平台：断开连接
            if (certSerialNo != null) {
                try {
                    monitorPlatformClient.notifyClientDisconnected(certSerialNo, "ukey_removed");
                    log.info("已通知管控平台断开连接");
                } catch (Exception e) {
                    log.error("通知管控平台断开失败", e);
                }
            }

            // 4. 清除本地认证状态
//            clientAuthService.clearAuthentication();

        } catch (Exception e) {
            log.error("UKey拔出处理异常", e);
        } finally {
            // 5. 重置状态机到IDLE
            lifecycleManager.reset("UKey已拔出");
            log.info("状态已重置为IDLE，等待UKey重新插入");
        }
    }
}
