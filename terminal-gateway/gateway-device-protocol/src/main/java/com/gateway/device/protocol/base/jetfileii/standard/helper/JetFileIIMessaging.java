package com.gateway.device.protocol.base.jetfileii.standard.helper;

import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.base.jetfileii.standard.codec.JetFileIICodec;
import com.gateway.device.protocol.base.jetfileii.standard.codec.JetFileIIResultMapper;
import com.gateway.device.protocol.base.jetfileii.standard.model.JetFileIIRequest;
import com.gateway.device.protocol.base.jetfileii.standard.model.PacketMessage;
import com.gateway.device.protocol.base.jetfileii.standard.transfer.FileTransfer;
import com.gateway.device.protocol.common.LittleEndianByteBufUtils;
import com.gateway.device.protocol.common.constant.StandardErrorCode;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * JetFileII 协议消息门面 —— 封装 编码→发送→接收→解码→结果映射 完整管线。
 *
 * <p>从 adapter 层 AbstractJetFileIIHandler 提取，属于 base 层纯协议能力。
 * 简单 Handler 调用 {@link #executeSimple} 走完整模板，
 * 复杂 Handler 可按需调用 {@link #encode} / {@link #decode} / {@link #mapResult} 分部使用。</p>
 */
@Slf4j
public class JetFileIIMessaging {

    private final JetFileIICodec codec;
    private final JetFileIIResultMapper resultMapper;

    public JetFileIIMessaging() {
        this.codec = new JetFileIICodec();
        this.resultMapper = new JetFileIIResultMapper();
    }

    // ════════════════════════════════════════════════════
    // 完整模板（简单命令）
    // ════════════════════════════════════════════════════

    /**
     * 从设备属性提取 gg（组地址），默认 1
     */
    public static int resolveGg(DeviceContext device) {
        return Optional.ofNullable(
                MapUtils.getInteger(device.getAttributes(), "gg")).orElse(1);
    }

    // ════════════════════════════════════════════════════
    // 分部操作（供复杂 Handler 按需调用）
    // ════════════════════════════════════════════════════

    /**
     * 从设备属性提取 uu（单元地址），默认 1
     */
    public static int resolveUu(DeviceContext device) {
        return Optional.ofNullable(
                MapUtils.getInteger(device.getAttributes(), "uu")).orElse(1);
    }

    /**
     * 执行完整的 编码→发送→接收→解码→结果映射 管线。
     *
     * @param transport 传输层
     * @param device    目标设备
     * @param request   已构建的协议请求
     * @param timeout   等待响应的超时
     * @return 统一命令结果
     */
    public CommandResult executeSimple(DeviceTransport transport, DeviceContext device,
                                       JetFileIIRequest request, Duration timeout) {
        long start = System.currentTimeMillis();

        byte[] payload = codec.encode(request);

        if (log.isDebugEnabled()) {
            log.debug("[{}] >>> transport request payload hex dump ({} bytes): {}",
                    device.getVendor(), payload.length,
                    LittleEndianByteBufUtils.toHex(payload));
        }

        try {
            CompletableFuture<byte[]> future = transport.sendAndReceive(device, payload, timeout);

            if (!request.isNeedReply()) {
                long cost = System.currentTimeMillis() - start;
                return CommandResult.builder()
                        .success(true).code(StandardErrorCode.SUCCESS)
                        .message("sent").costMillis(cost).build();
            }

            byte[] response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);

            PacketMessage pkt = codec.decode(response);
            if (pkt == null) {
                return CommandResult.failure(StandardErrorCode.PROTOCOL_ERROR, "无法解析响应报文");
            }

            CommandResult result = resultMapper.map(pkt);
            result.setCostMillis(System.currentTimeMillis() - start);
            return result;

        } catch (TimeoutException e) {
            long cost = System.currentTimeMillis() - start;
            return CommandResult.builder()
                    .success(false).code(StandardErrorCode.TIMEOUT)
                    .message("设备响应超时").costMillis(cost).build();
        } catch (ExecutionException e) {
            long cost = System.currentTimeMillis() - start;
            Throwable cause = e.getCause();
            return CommandResult.builder()
                    .success(false).code(StandardErrorCode.TRANSPORT_ERROR)
                    .message(cause != null ? cause.getMessage() : e.getMessage())
                    .costMillis(cost).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long cost = System.currentTimeMillis() - start;
            return CommandResult.builder()
                    .success(false).code(StandardErrorCode.SYSTEM_ERROR)
                    .message("线程中断").costMillis(cost).build();
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            return CommandResult.builder()
                    .success(false).code(StandardErrorCode.SYSTEM_ERROR)
                    .message(e.getMessage()).costMillis(cost).build();
        }
    }

    /**
     * 将请求编码为协议字节
     */
    public byte[] encode(JetFileIIRequest request) {
        return codec.encode(request);
    }

    /**
     * 将响应字节解码为报文
     */
    public PacketMessage decode(byte[] response) {
        return codec.decode(response);
    }

    // ════════════════════════════════════════════════════
    // 静态工具
    // ════════════════════════════════════════════════════

    /**
     * 将报文映射为统一命令结果
     */
    public CommandResult mapResult(PacketMessage response) {
        return resultMapper.map(response);
    }

    /**
     * 创建 FileTransfer 实例（自动解析 gg/uu）
     */
    public FileTransfer createFileTransfer(DeviceTransport transport, DeviceContext device) {
        int gg = resolveGg(device);
        int uu = resolveUu(device);
        return new FileTransfer(transport, device)
                .setAddr(gg, uu)
                .setVerbose(false);
    }
}
