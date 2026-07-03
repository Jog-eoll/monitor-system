package com.gateway.device.protocol.adapter.colorlight.standard;

import com.gateway.device.protocol.adapter.colorlight.standard.handler.AbstractColorLightHttpHandler;
import com.gateway.device.protocol.adapter.colorlight.standard.handler.info.ColorLightDeviceInfoGetHandler;
import com.gateway.device.protocol.adapter.colorlight.standard.handler.media.ColorLightMediaDeleteHandler;
import com.gateway.device.protocol.adapter.colorlight.standard.handler.media.ColorLightMediaMultiUploadHandler;
import com.gateway.device.protocol.adapter.colorlight.standard.handler.media.ColorLightMediaUploadHandler;
import com.gateway.device.protocol.adapter.colorlight.standard.handler.media.ColorLightTextUploadHandler;
import com.gateway.device.protocol.adapter.colorlight.standard.handler.operate.*;
import com.gateway.device.protocol.adapter.colorlight.standard.handler.playlist.*;
import com.gateway.device.protocol.api.*;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpRequest;
import com.gateway.device.protocol.base.colorlight.standard.codec.ColorLightHttpResponse;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.constant.DeviceVendor;
import com.gateway.device.protocol.common.constant.TransportType;
import com.gateway.device.protocol.model.CommandResult;
import com.gateway.device.protocol.model.DeviceContext;
import com.gateway.device.protocol.model.params.depend.CommandParams;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * ColorLight 厂商协议适配器 —— HTTP REST API，Handler 注册模式。
 *
 * <p>每个 {@link DeviceCapability} 对应一个 {@link CapabilityHandler} 实现，
 * 执行时通过闭包分发，消除 switch/case。
 * Handler 映射表由 Adapter 独立维护，不与其它 Adapter 共享。</p>
 *
 * <p>传输层使用 Netty HttpClientCodec + HttpObjectAggregator（TransportType.HTTP），
 * 默认端口 8989。Basic Auth 凭据通过 {@link ColorLightCredentialStore} 按设备持久化复用。</p>
 */
public class ColorLightAdapter implements VendorProtocolAdapter {

    private final ProtocolCodec<ColorLightHttpRequest, ColorLightHttpResponse> codec;

    @Getter
    private final Map<DeviceCapability<?>, CapabilityHandler<?>> handlers = new HashMap<>();

    @Getter
    private final Map<DeviceCapability<?>, BiFunction<DeviceContext, CommandParams, CommandResult>> dispatchers = new HashMap<>();

    public ColorLightAdapter(DeviceTransport transport,
                             ColorLightCredentialStore credentialStore,
                             ProtocolCodec<ColorLightHttpRequest, ColorLightHttpResponse> codec,
                             DeviceAuthStore authStore,
                             String fontLocalPath) {
        this.codec = codec;

        // ── INFO ──
        // 设备信息查询
        register(new ColorLightDeviceInfoGetHandler(transport, credentialStore, codec));

        // ── MEDIA ──
        // ── 文字 ──
        register(new ColorLightTextUploadHandler(transport, credentialStore, codec));
        // ── 图片 ──
        register(new ColorLightMediaUploadHandler(transport, credentialStore, codec, CommonDeviceCapability.IMAGE_UPLOAD));
        // ── 视频 ──
        register(new ColorLightMediaUploadHandler(transport, credentialStore, codec, CommonDeviceCapability.VIDEO_UPLOAD));
        // ── 混合多媒体（图片+视频） ──
        register(new ColorLightMediaMultiUploadHandler(transport, credentialStore, codec));
        // ── 通用媒体删除 ──
        register(new ColorLightMediaDeleteHandler(transport, credentialStore, codec, CommonDeviceCapability.MEDIA_DELETE));
        register(new ColorLightPlaylistClearHandler(transport, credentialStore, codec, CommonDeviceCapability.MEDIA_CLEAR));
        // ── PROGRAM ──
        // ── 播放列表 ──
        register(new ColorLightPlaylistGetHandler(transport, credentialStore, codec));
        register(new ColorLightPlaylistSetHandler(transport, credentialStore, codec));
        register(new ColorLightPlaylistClearHandler(transport, credentialStore, codec, CommonDeviceCapability.PLAYLIST_CLEAR));
        // ── 字体管理 ──
        register(new ColorLightFontsGetHandler(transport, credentialStore, codec));
        register(new ColorLightFontsSyncHandler(transport, credentialStore, codec, fontLocalPath));
        register(new ColorLightFontsClearHandler(transport, credentialStore, codec));
        // ── 音量调节 ──
        register(new ColorLightVolumeGetHandler(transport, credentialStore, codec));
        register(new ColorLightVolumeSetHandler(transport, credentialStore, codec));

        // ── OPERATE ──
        // 亮度调节
        register(new ColorLightBrightnessHandler(transport, credentialStore, codec));
        // 时间同步
        register(new ColorLightTimeSyncHandler(transport, credentialStore, codec));
        // 黑屏开关
        register(new ColorLightScreenBlackoutHandler(transport, credentialStore, codec));
        // 重启
        register(new ColorLightRebootHandler(transport, credentialStore, codec));
        // 休眠
        register(new ColorLightSleepHandler(transport, credentialStore, codec));
        // 唤醒
        register(new ColorLightWakeupHandler(transport, credentialStore, codec));
        // IP 配置
        register(new ColorLightIpConfigHandler(transport, credentialStore, codec));
        // AP 热点开关
        register(new ColorLightApSwitchHandler(transport, credentialStore, codec));
        // NTP 服务器配置
        register(new ColorLightNtpConfigHandler(transport, credentialStore, codec));
        register(new ColorLightNtpGetHandler(transport, credentialStore, codec));

        // 注入认证存储（用于已持久化凭据查找）
        if (authStore != null) {
            handlers.values().forEach(h -> {
                if (h instanceof AbstractColorLightHttpHandler) {
                    ((AbstractColorLightHttpHandler<?>) h).setAuthStore(authStore);
                }
            });
        }
    }

    // ════════════════════════════════════════════════════
    // VendorProtocolAdapter
    // ════════════════════════════════════════════════════

    @Override
    public DeviceVendor vendor() {
        return DeviceVendor.COLOR_LIGHT_STANDARD;
    }

    @Override
    public TransportType transportType() {
        return TransportType.HTTP;
    }

    @Override
    public ProtocolCodec<?, ?> codec() {
        return codec;
    }
}
