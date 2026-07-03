package com.gateway.device.protocol.base.novastar.viplexcore.builder;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gateway.device.protocol.common.JsonCustomMapper;
import com.gateway.device.protocol.common.constant.MediaType;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 图片页面构建器 — 单图或多图轮播。
 *
 * <p>单容器（WCtype=PICTURE, pickPolicy=ORDER），N 个 widget 按序播放。
 * 单图场景即 {@code images.size() == 1}，无需特殊分支。</p>
 */
public class ViplexImagePageBuilder {

    /**
     * 构建图片页面（单图或多图统一入口）。
     *
     * @param programId 节目 ID
     * @param pageId    页面 ID
     * @param images    图片文件信息列表（至少 1 个）
     * @param width     屏宽（保留，当前未写入 widget）
     * @param height    屏高（保留，当前未写入 widget）
     */
    public ObjectNode buildPage(String programId, int pageId,
                                List<ImageFileInfo> images,
                                int width, int height) {
        if (CollectionUtils.isEmpty(images)) {
            throw new IllegalArgumentException("images 不能为空");
        }

        // 为每张图片构建 widget，id 自 WIDGET_BASE_ID 递增
        List<ObjectNode> widgetList = new ArrayList<>(images.size());
        for (int i = 0; i < images.size(); i++) {
            widgetList.add(buildWidget(images.get(i), ViplexBaseBuilder.WIDGET_BASE_ID + i));
        }

        ArrayNode widgetArray = JsonCustomMapper.get().createArrayNode();
        for (ObjectNode w : widgetList) {
            widgetArray.add(w);
        }

        ObjectNode contents = ViplexBaseBuilder.buildContents(widgetArray);
        ObjectNode container = ViplexBaseBuilder.buildContainer(
                "图片1", "PICTURE", 1,
                ViplexBaseBuilder.buildContainerBorder(false),
                contents);

        return ViplexBaseBuilder.wrapPage(programId, pageId, container);
    }

    /**
     * 构建单个图片 widget。
     */
    private ObjectNode buildWidget(ImageFileInfo info, int widgetId) {
        MediaFileInfo mediaInfo = new MediaFileInfo(
                info.getFilePath(), info.getMd5(), info.getFileName(),
                MediaType.IMAGE, info.getFileSize(), 10000);
        return ViplexBaseBuilder.buildPictureWidget(mediaInfo, widgetId);
    }
}
