package com.gateway.device.protocol.adapter.jetfileii.standard;

import com.gateway.device.protocol.adapter.jetfileii.standard.handler.info.JetFileIIDeviceInfoGetHandler;
import com.gateway.device.protocol.adapter.jetfileii.standard.handler.media.*;
import com.gateway.device.protocol.adapter.jetfileii.standard.handler.operate.*;
import com.gateway.device.protocol.adapter.jetfileii.standard.handler.playlist.*;
import com.gateway.device.protocol.api.CapabilityHandler;
import com.gateway.device.protocol.api.DeviceTransport;
import com.gateway.device.protocol.api.ProtocolCodec;
import com.gateway.device.protocol.api.VendorProtocolAdapter;
import com.gateway.device.protocol.base.jetfileii.standard.codec.JetFileIICodec;
import com.gateway.device.protocol.base.jetfileii.standard.command.FileType;
import com.gateway.device.protocol.base.jetfileii.standard.file.JetFileIIFileExtensions;
import com.gateway.device.protocol.base.jetfileii.standard.helper.JetFileIIMessaging;
import com.gateway.device.protocol.base.jetfileii.standard.text.NmgTextStyle;
import com.gateway.device.protocol.common.capability.CommonDeviceCapability;
import com.gateway.device.protocol.common.capability.depend.DeviceCapability;
import com.gateway.device.protocol.common.capability.expand.JetFileIICapability;
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
 * JetFileII 厂商协议适配器 —— Handler 注册模式。
 *
 * <p>每个 {@link DeviceCapability} 对应一个 {@link CapabilityHandler} 实现，
 * 执行时通过闭包分发，消除 switch/case。
 * Handler 映射表由 Adapter 独立维护，不与其它 Adapter 共享。</p>
 */
public class JetFileIIAdapter implements VendorProtocolAdapter {

    private final JetFileIICodec codec;
    /**
     * capability → handler 映射（用于初始化配置和能力查询）
     */
    @Getter
    private final Map<DeviceCapability<?>, CapabilityHandler<?>> handlers = new HashMap<>();
    /**
     * capability → 类型安全 dispatch 闭包（用于运行时执行）
     */
    @Getter
    private final Map<DeviceCapability<?>, BiFunction<DeviceContext, CommandParams, CommandResult>> dispatchers = new HashMap<>();

    public JetFileIIAdapter(DeviceTransport transport,
                            String defaultFileName,
                            NmgTextStyle textStyle,
                            String fontLocalPath,
                            JetFileIIFileExtensions extensions) {
        JetFileIIMessaging messaging = new JetFileIIMessaging();
        this.codec = new JetFileIICodec();

        // ── BASE ──
        // 设备信息查询
        register(new JetFileIIDeviceInfoGetHandler(messaging, transport));

        // ── 图片 ──
        register(new JetFileIIResourceListQueryHandler(messaging, transport, CommonDeviceCapability.IMAGE_LIST_QUERY, extensions.getImage()));
        register(new JetFileIIResourceUploadHandler(messaging, transport, CommonDeviceCapability.IMAGE_UPLOAD, extensions));
        register(new JetFileIIResourceDownloadHandler(messaging, transport, CommonDeviceCapability.IMAGE_DOWNLOAD));
        register(new JetFileIIResourceDeleteHandler(messaging, transport, CommonDeviceCapability.IMAGE_DELETE, FileType.PICTURE));

        // ── 文字 ──
        register(new JetFileIIResourceListQueryHandler(messaging, transport, CommonDeviceCapability.TEXT_LIST_QUERY, extensions.getNmg()));
        register(new JetFileIITextUploadHandler(messaging, transport, defaultFileName, textStyle));
        register(new JetFileIITextDownloadHandler(messaging, transport, defaultFileName));
        register(new JetFileIIResourceDeleteHandler(messaging, transport, CommonDeviceCapability.TEXT_DELETE, FileType.TEXT, defaultFileName));

        // ── 视频 ──
        register(new JetFileIIResourceListQueryHandler(messaging, transport, CommonDeviceCapability.VIDEO_LIST_QUERY, extensions.getVideo()));
        register(new JetFileIIResourceUploadHandler(messaging, transport, CommonDeviceCapability.VIDEO_UPLOAD, extensions));
        register(new JetFileIIResourceDownloadHandler(messaging, transport, CommonDeviceCapability.VIDEO_DOWNLOAD));
        register(new JetFileIIResourceDeleteHandler(messaging, transport, CommonDeviceCapability.VIDEO_DELETE, FileType.FLW));

        // ── Nmg ──
        register(new JetFileIIResourceListQueryHandler(messaging, transport, JetFileIICapability.NMG_FILE_LIST_QUERY, extensions.getNmg()));
        register(new JetFileIIResourceUploadHandler(messaging, transport, JetFileIICapability.NMG_FILE_UPLOAD, extensions));
        register(new JetFileIIResourceDownloadHandler(messaging, transport, JetFileIICapability.NMG_FILE_DOWNLOAD));
        register(new JetFileIIResourceDeleteHandler(messaging, transport, JetFileIICapability.NMG_FILE_DELETE, FileType.TEXT));

        // ── Pmg ──
        register(new JetFileIIResourceListQueryHandler(messaging, transport, JetFileIICapability.PMG_FILE_LIST_QUERY, extensions.getPmg()));
        register(new JetFileIIResourceUploadHandler(messaging, transport, JetFileIICapability.PMG_FILE_UPLOAD, extensions));
        register(new JetFileIIResourceDownloadHandler(messaging, transport, JetFileIICapability.PMG_FILE_DOWNLOAD));
        register(new JetFileIIResourceDeleteHandler(messaging, transport, JetFileIICapability.PMG_FILE_DELETE, FileType.ARRAY_PICTURE));

        // ── Qst ──
        register(new JetFileIIResourceListQueryHandler(messaging, transport, JetFileIICapability.QST_FILE_LIST_QUERY, extensions.getQst()));
        register(new JetFileIIResourceUploadHandler(messaging, transport, JetFileIICapability.QST_FILE_UPLOAD, extensions));
        register(new JetFileIIResourceDownloadHandler(messaging, transport, JetFileIICapability.QST_FILE_DOWNLOAD));
        register(new JetFileIIResourceDeleteHandler(messaging, transport, JetFileIICapability.QST_FILE_DELETE, FileType.ARRAY_QST));

        // ── 混合媒体批量上传 ──
        register(new JetFileIIMediaMultiUploadHandler(messaging, transport, extensions));

        // ── 播放列表 ──
        register(new JetFileIIPlaylistClearHandler(messaging, transport));
        register(new JetFileIIPlaylistGetHandler(messaging, transport));
        register(new JetFileIIPlaylistSetHandler(messaging, transport));

        // 重启
        register(new JetFileIIRestartHandler(messaging, transport));
        // 黑屏开关
        register(new JetFileIIScreenBlackoutHandler(messaging, transport));
        // 色彩测试
        register(new JetFileIIColorTestHandler(messaging, transport));
        // 亮度调节
        register(new JetFileIIBrightnessHandler(messaging, transport));
        // 时间同步
        register(new JetFileIITimeSyncHandler(messaging, transport));
        // 字体查询
        register(new JetFileIIFontsGetHandler(messaging, transport));
        // 字体同步
        register(new JetFileIIFontSyncHandler(messaging, transport, fontLocalPath));
        // IP 配置
        register(new JetFileIIIpConfigHandler(messaging, transport));
        // 点阵像素宽高配置
        register(new JetFileIIScreenAttributeHandler(messaging, transport));

    }

    // ════════════════════════════════════════════════════
    // VendorProtocolAdapter
    // ════════════════════════════════════════════════════

    @Override
    public DeviceVendor vendor() {
        return DeviceVendor.JET_FILE_II_STANDARD;
    }

    @Override
    public TransportType transportType() {
        return TransportType.UDP;
    }

    @Override
    public ProtocolCodec<?, ?> codec() {
        return codec;
    }
}
