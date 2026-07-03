package com.gateway.device.protocol.adapter.novastar.viplexcore;

import com.gateway.device.protocol.adapter.novastar.viplexcore.handler.info.NovaViplexCoreConfigurationGetHandler;
import com.gateway.device.protocol.adapter.novastar.viplexcore.handler.info.NovaViplexCoreDeviceInfoGetHandler;
import com.gateway.device.protocol.adapter.novastar.viplexcore.handler.info.NovaViplexCoreDeviceProductInfoHandler;
import com.gateway.device.protocol.adapter.novastar.viplexcore.handler.info.NovaViplexCoreDisplayInfoHandler;
import com.gateway.device.protocol.adapter.novastar.viplexcore.handler.media.*;
import com.gateway.device.protocol.adapter.novastar.viplexcore.handler.operate.*;
import com.gateway.device.protocol.adapter.novastar.viplexcore.handler.playlist.*;
import com.gateway.device.protocol.api.CapabilityHandler;
import com.gateway.device.protocol.api.ProtocolCodec;
import com.gateway.device.protocol.api.VendorProtocolAdapter;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexCoreChannel;
import com.gateway.device.protocol.base.novastar.viplexcore.builder.ViplexProgramPipeline;
import com.gateway.device.protocol.base.novastar.viplexcore.text.NovaViplexCoreTextStyle;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.capability.expand.NovaViplexCoreCapability;
import com.gateway.device.protocol.common.constant.DeviceVendor;
import com.gateway.device.protocol.common.constant.TransportType;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.depend.CommandParams;
import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * NovaStandard ViplexCore SDK 协议适配器 —— 通过 JNA 调用原生 SDK 实现设备管控。
 *
 * <p>通用能力（设备信息/黑屏/亮度/电源/时间同步）使用 {@code CommonDeviceCapability}，
 * 协议私有能力（音量/节目/屏体电源状态等）使用 {@link NovaViplexCoreCapability}。
 * Handler 映射表由 Adapter 独立维护，不与其它 Adapter 共享。</p>
 *
 * <p>传输层由 SDK 内部管理，无需 {@code DeviceTransport} 和自定义编解码器。</p>
 */
public class NovaViplexCoreAdapter implements VendorProtocolAdapter {

    @Getter
    private final Map<DeviceCapability<?>, CapabilityHandler<?>> handlers = new HashMap<>();
    @Getter
    private final Map<DeviceCapability<?>, BiFunction<DeviceContext, CommandParams, CommandResult>> dispatchers = new HashMap<>();

    public NovaViplexCoreAdapter(ViplexCoreChannel channel,
                                 NovaViplexCoreTextStyle textStyle,
                                 ViplexProgramPipeline pipeline,
                                 String fontLocalPath) {

        // ── BASE ──
        // 设备信息查询
        register(new NovaViplexCoreDeviceInfoGetHandler(channel, pipeline, textStyle));

        // ── 文件删除（通用） ──
        register(new NovaViplexCoreMediaClearAllHandler(channel, pipeline, textStyle));

        // ── 图片 ──
        register(new NovaViplexCoreImageUploadHandler(channel, pipeline, textStyle));
        // ── 混合多媒体（图片+视频） ──
        register(new NovaViplexCoreMediaMultiUploadHandler(channel, pipeline, textStyle));
        // ── 文字 ──
        register(new NovaViplexCoreTextUploadHandler(channel, pipeline, textStyle));
        // ── 视频 ──
        register(new NovaViplexCoreVideoUploadHandler(channel, pipeline, textStyle));

        // ── 播放列表 ──
        register(new NovaViplexCorePlaylistClearHandler(channel, pipeline, textStyle));
        register(new NovaViplexCorePlaylistGetHandler(channel, pipeline, textStyle));
        register(new NovaViplexCorePlaylistSetHandler(channel, pipeline, textStyle));

        // 通用能力（CommonDeviceCapability）
        // 黑屏开关
        register(new NovaViplexCoreScreenBlackoutHandler(channel, pipeline, textStyle));
        // 亮度调节
        register(new NovaViplexCoreBrightnessHandler(channel, pipeline, textStyle));
        // 重启
        register(new NovaViplexCorePowerControlHandler(channel, pipeline, textStyle));
        // AP 热点开关
        register(new NovaViplexCoreApNetworkSwitchHandler(channel, pipeline, textStyle));
        // 时间同步
        register(new NovaViplexCoreTimeSyncHandler(channel, pipeline, textStyle));
        // NTP 配置
        register(new NovaViplexCoreNtpSetHandler(channel, pipeline, textStyle));
        // IP 配置
        register(new NovaViplexCoreIpConfigHandler(channel, pipeline, textStyle));
        // 点阵配屏
        register(new NovaViplexCoreScreenAttributeSetHandler(channel, pipeline, textStyle));

        // ViplexCore 私有能力（NovaViplexCoreCapability）
        // 设备配置获取
        register(new NovaViplexCoreConfigurationGetHandler(channel, pipeline, textStyle));
        // 音量调节
        register(new NovaViplexCoreVolumeGetHandler(channel, pipeline, textStyle));
        register(new NovaViplexCoreVolumeSetHandler(channel, pipeline, textStyle));

        // 私有能力 — 监控 & 显示
        // 产品信息查询
        register(new NovaViplexCoreDeviceProductInfoHandler(channel, pipeline, textStyle));
        // 显示信息查询
        register(new NovaViplexCoreDisplayInfoHandler(channel, pipeline, textStyle));
        // 设置显示屏分辨率
        register(new NovaViplexCoreResolutionSetHandler(channel, pipeline, textStyle));

        // ── 字体管理 ──
        // 字体查询
        register(new NovaViplexCoreFontsGetHandler(channel, pipeline, textStyle));
        // 字体同步（公共能力 FONTS_SYNC）
        register(new NovaViplexCoreFontsSyncHandler(channel, pipeline, textStyle, fontLocalPath));
    }

    // ════════════════════════════════════════════════════════
    // VendorProtocolAdapter
    // ════════════════════════════════════════════════════════

    @Override
    public DeviceVendor vendor() {
        return DeviceVendor.NOVA_STAR_VIPLEX_CORE;
    }

    @Override
    public TransportType transportType() {
        return TransportType.NATIVE_SDK;
    }

    @Override
    public ProtocolCodec<?, ?> codec() {
        return null;
    }

    @Override
    public Set<DeviceCapability<?>> capabilities() {
        return Collections.unmodifiableSet(handlers.keySet());
    }

    @Override
    public boolean supports(DeviceContext device, DeviceCapability<?> capability) {
        if (!DeviceVendor.NOVA_STAR_VIPLEX_CORE.equals(device.getVendor())) return false;
        if (capability == null) return false;
        return handlers.containsKey(capability);
    }
}
