package com.gateway.device.protocol.adapter.jetfileii.standard.handler.operate;

import com.gateway.device.protocol.adapter.jetfileii.standard.handler.AbstractJetFileIIHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.base.jetfileii.standard.command.StatusCode;
import com.gateway.device.protocol.base.jetfileii.standard.helper.JetFileIIMessaging;
import com.gateway.device.protocol.base.jetfileii.standard.helper.ScreenAttributeHelper;
import com.gateway.device.protocol.base.jetfileii.standard.model.JetFileIIRequest;
import com.gateway.device.protocol.base.jetfileii.standard.model.PacketMessage;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.ScreenAttributeParams;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * JetFileII 点阵像素宽高配置处理器 —— 通过 MULTI_WIN 读写实现。
 *
 * <p>流程：READ_MULTI_WIN 读取当前 88 字节窗口配置 → 修改 width/height →
 * WRITE_MULTI_WIN 写回。重用 {@link ScreenAttributeHelper} 共享协议常量与方法。</p>
 */
@Slf4j
public class JetFileIIScreenAttributeHandler extends AbstractJetFileIIHandler<ScreenAttributeParams> {

    public JetFileIIScreenAttributeHandler(JetFileIIMessaging messaging, DeviceTransport transport) {
        super(messaging, transport);
    }

    @Override
    public DeviceCapability<ScreenAttributeParams> capability() {
        return CommonDeviceCapability.SCREEN_ATTRIBUTE_SET;
    }

    @Override
    public CommandResult execute(DeviceContext device, ScreenAttributeParams params) {
        int targetWidth = (params != null) ? params.getWidth() : 64;
        int targetHeight = (params != null) ? params.getHeight() : 64;

        int gg = JetFileIIMessaging.resolveGg(device);
        int uu = JetFileIIMessaging.resolveUu(device);

        try {
            // ── 1. 读取当前 MULTI_WIN 配置 ──
            JetFileIIRequest readReq = ScreenAttributeHelper.buildReadRequest(gg, uu);

            byte[] readPayload = messaging().encode(readReq);
            CompletableFuture<byte[]> readFuture = transport().sendAndReceive(device, readPayload, getTimeout());
            byte[] readRespBytes = readFuture.get(getTimeout().toMillis(), TimeUnit.MILLISECONDS);
            PacketMessage readPkt = messaging().decode(readRespBytes);

            byte[] originalData = readPkt != null ? readPkt.getData() : null;
            if (!ScreenAttributeHelper.isValidData(originalData)) {
                String detail = String.format("READ_MULTI_WIN 返回数据异常, len=%s",
                        originalData != null ? originalData.length : "null");
                log.warn("[{}] {}", device.getIp(), detail);
                return CommandResult.failure(StandardErrorCode.PROTOCOL_ERROR, detail);
            }

            int oldWidth = ScreenAttributeHelper.readWidth(originalData);
            int oldHeight = ScreenAttributeHelper.readHeight(originalData);

            log.info("[{}] 读取当前点阵: width={}, height={}, 目标: width={}, height={}",
                    device.getIp(), oldWidth, oldHeight, targetWidth, targetHeight);

            // ── 2. 修改 width/height ──
            byte[] modifiedData = ScreenAttributeHelper.withSize(originalData, targetWidth, targetHeight);

            // ── 3. 写回 MULTI_WIN 配置 ──
            JetFileIIRequest writeReq = ScreenAttributeHelper.buildWriteRequest(gg, uu, modifiedData,
                    ScreenAttributeHelper.DEFAULT_WINDOW_INDEX);

            byte[] writePayload = messaging().encode(writeReq);
            CompletableFuture<byte[]> writeFuture = transport().sendAndReceive(device, writePayload, getTimeout());
            byte[] writeRespBytes = writeFuture.get(getTimeout().toMillis(), TimeUnit.MILLISECONDS);
            PacketMessage writePkt = messaging().decode(writeRespBytes);

            if (writePkt == null || !writePkt.isStatusReply() || !StatusCode.isOk(writePkt.getStatusCode())) {
                short code = writePkt != null ? writePkt.getStatusCode() : -1;
                String detail = String.format("WRITE_MULTI_WIN 失败: 0x%04X", code & 0xFFFF);
                log.warn("[{}] {}", device.getIp(), detail);
                return CommandResult.failure(StandardErrorCode.PROTOCOL_ERROR, detail);
            }

            log.info("[{}] 点阵像素宽高已设置: width={}, height={}", device.getIp(), targetWidth, targetHeight);
            return CommandResult.success(String.format("width=%d, height=%d", targetWidth, targetHeight));

        } catch (TimeoutException e) {
            log.warn("[{}] 设置点阵像素宽高超时", device.getIp());
            return CommandResult.timeout();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CommandResult.failure(StandardErrorCode.SYSTEM_ERROR, "线程中断");
        } catch (Exception e) {
            log.error("[{}] 设置点阵像素宽高失败: {}", device.getIp(), e.getMessage(), e);
            return CommandResult.failure(StandardErrorCode.TRANSPORT_ERROR, e.getMessage());
        }
    }
}
