package com.gateway.device.protocol.base.novastar.viplexcore.builder;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gateway.device.protocol.common.JsonCustomMapper;
import com.gateway.device.protocol.common.constant.MediaType;

/**
 * 视频页面构建器 — 单视频全屏播放。
 *
 * <p>单容器（WCtype=VIDEO），单 widget（含 volume metadata）。</p>
 */
public class ViplexVideoPageBuilder {

    /**
     * 构建视频页面。
     *
     * @param programId 节目 ID
     * @param pageId    页面 ID
     * @param filePath  视频文件路径（保留，当前未写入 JSON）
     * @param md5       文件 MD5
     * @param fileName  文件名（含扩展名）
     * @param width     屏宽（保留）
     * @param height    屏高（保留）
     * @param fileSize  文件字节大小
     */
    public ObjectNode buildPage(String programId, int pageId,
                                String filePath, String md5,
                                String fileName,
                                int width, int height, int fileSize) {
        // ── widget ──
        MediaFileInfo info = new MediaFileInfo(filePath, md5, fileName, MediaType.VIDEO, fileSize, 10000);
        ObjectNode widget = ViplexBaseBuilder.buildVideoWidget(info, ViplexBaseBuilder.WIDGET_BASE_ID);

        // ── contents ──
        ArrayNode widgetArray = JsonCustomMapper.get().createArrayNode().add(widget);
        ObjectNode contents = ViplexBaseBuilder.buildContents(widgetArray);

        // ── container ──
        ObjectNode container = ViplexBaseBuilder.buildContainer(
                "视频1", "VIDEO", 1,
                ViplexBaseBuilder.buildContainerBorder(false),
                contents);

        return ViplexBaseBuilder.wrapPage(programId, pageId, container);
    }
}
