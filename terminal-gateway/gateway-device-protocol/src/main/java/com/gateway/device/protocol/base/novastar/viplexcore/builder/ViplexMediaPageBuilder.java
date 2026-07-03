package com.gateway.device.protocol.base.novastar.viplexcore.builder;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gateway.device.protocol.common.JsonCustomMapper;
import com.gateway.device.protocol.common.constant.MediaType;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 混合媒体页面构建器 — 每个媒体文件独立页面，对齐 NovaStar VP0001 节目结构。
 *
 * <p>参考抓包 VP0001（T30, 1 视频 + 2 图片）：3 个 sceneItems，每页 1 容器 1 widget。
 * 与 {@link ViplexImagePageBuilder}（单页单容器多图轮播）属于不同节目模式。</p>
 *
 * <p>ID 分配（索引 i）：widgetId=100000+i, containerId=200000+i, pageId=startPageId+i</p>
 */
public class ViplexMediaPageBuilder {

    /**
     * 为每个媒体文件构建独立页面。
     *
     * @param programId   节目 ID
     * @param startPageId 起始页面 ID（对齐单文件 handler 使用 1）
     * @param mediaFiles  已按优先级排序的媒体文件列表
     * @return 独立页面 JSON 列表，每页 1 容器 1 widget
     */
    public List<ObjectNode> buildPages(String programId, int startPageId,
                                       List<MediaFileInfo> mediaFiles) {
        if (CollectionUtils.isEmpty(mediaFiles)) {
            throw new IllegalArgumentException("mediaFiles 不能为空");
        }

        int picCount = 0;
        int vidCount = 0;
        List<ObjectNode> pages = new ArrayList<>(mediaFiles.size());

        for (int i = 0; i < mediaFiles.size(); i++) {
            MediaFileInfo info = mediaFiles.get(i);
            int widgetId = ViplexBaseBuilder.WIDGET_BASE_ID + i;
            int containerId = ViplexBaseBuilder.CONTAINER_BASE_ID + i;
            int pageId = startPageId + i;

            // ── widget ──
            ObjectNode widget;
            String wcType;
            String containerName;
            if (info.getMediaType() == MediaType.VIDEO) {
                vidCount++;
                wcType = "VIDEO";
                containerName = "视频" + vidCount;
                widget = ViplexBaseBuilder.buildVideoWidget(info, widgetId);
            } else {
                picCount++;
                wcType = "PICTURE";
                containerName = "图片" + picCount;
                widget = ViplexBaseBuilder.buildPictureWidget(info, widgetId);
            }
            widget.put("zOrder", 1);

            // ── contents ──
            ArrayNode widgetArray = JsonCustomMapper.get().createArrayNode().add(widget);
            ObjectNode contents = ViplexBaseBuilder.buildContents(widgetArray);

            // ── container ──
            ObjectNode container = ViplexBaseBuilder.buildContainer(
                    containerName, wcType, 1,
                    ViplexBaseBuilder.buildContainerBorder(false),
                    contents);
            container.put("id", containerId);
            container.put("zOrder", 1);

            // ── page ──
            pages.add(ViplexBaseBuilder.wrapPage(programId, pageId, container));
        }

        return pages;
    }
}
