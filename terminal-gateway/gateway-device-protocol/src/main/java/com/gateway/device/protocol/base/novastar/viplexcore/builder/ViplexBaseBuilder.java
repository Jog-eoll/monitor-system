package com.gateway.device.protocol.base.novastar.viplexcore.builder;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gateway.device.protocol.common.JsonCustomMapper;

import java.util.List;
import java.util.UUID;

/**
 * ViplexCore 节目 JSON 共享基础构建块 —— 纯静态工具方法。
 *
 * <p>为各媒体类型构建器提供 Border、Layout、Animation、Constraint、
 * Container、Page 等可复用的 JSON 片段。</p>
 */
public final class ViplexBaseBuilder {

    static final int WIDGET_BASE_ID = 100000;
    static final int CONTAINER_BASE_ID = 200000;

    private ViplexBaseBuilder() {
    }

    // ═══ Border ═══

    static ObjectNode buildBorderBase(String bgColor, int width, int speed, boolean isLocked) {
        ObjectNode border = JsonCustomMapper.get().createObjectNode();
        border.put("style", 0);
        border.put("width", width);
        border.put("backgroundColor", bgColor);
        border.put("foregroundColor", "#FF008000");
        ObjectNode aspect = JsonCustomMapper.get().createObjectNode();
        aspect.put("type", 0);
        aspect.put("isLocked", isLocked);
        border.set("aspectRatio", aspect);
        ObjectNode effects = JsonCustomMapper.get().createObjectNode();
        effects.put("speed", speed);
        effects.put("animation", "CLOCK_WISE");
        effects.put("isHeadTail", false);
        effects.put("headTailSpacing", "10");
        effects.put("speedByPixelEnable", false);
        border.set("effects", effects);
        return border;
    }

    static ObjectNode buildPageBorder() {
        ObjectNode border = buildBorderBase("#FFFF0000", 1, 60, true);
        border.put("styleForExpress", 0);
        return border;
    }

    static ObjectNode buildWidgetBorder() {
        ObjectNode border = buildBorderBase("#FF000000", 1, 3, true);
        border.put("name", "border");
        border.put("borderThickness", "0px,0px,0px,0px");
        border.put("cornerRadius", "2%");
        border.put("styleForExpress", 0);
        return border;
    }

    static ObjectNode buildContainerBorder(boolean isText) {
        if (isText) {
            ObjectNode border = buildBorderBase("#FF000000", 1, 3, false);
            border.put("name", "border");
            border.put("borderThickness", "0px,0px,0px,0px");
            border.put("cornerRadius", "2%");
            border.put("styleForExpress", 0);
            return border;
        } else {
            ObjectNode border = buildBorderBase("#FFFF0000", 0, 2, true);
            border.put("styleForExpress", 0);
            return border;
        }
    }

    // ═══ Layout ═══

    /**
     * Widget 全屏布局：百分比 100%×100% + 浮点 100.0×100.0
     */
    static ObjectNode buildFullWidgetLayout() {
        ObjectNode layout = JsonCustomMapper.get().createObjectNode();
        layout.put("x", "0%");
        layout.put("y", "0%");
        layout.put("width", "100%");
        layout.put("height", "100%");
        layout.put("xNum", 0.0);
        layout.put("yNum", 0.0);
        layout.put("widthNum", 100.0);
        layout.put("heightNum", 100.0);
        return layout;
    }

    /**
     * Container 全屏布局：小数分数制 1.0×1.0
     */
    static ObjectNode buildFullContainerLayout() {
        ObjectNode layout = JsonCustomMapper.get().createObjectNode();
        layout.put("x", "0.0");
        layout.put("y", "0.0");
        layout.put("width", "1.0");
        layout.put("height", "1.0");
        return layout;
    }

    // ═══ Animation ═══

    static ObjectNode buildNoAnimation() {
        ObjectNode anim = JsonCustomMapper.get().createObjectNode();
        anim.put("type", 0);
        anim.put("duration", 1000);
        return anim;
    }

    // ═══ Constraint ═══

    static ObjectNode buildDefaultConstraint() {
        ObjectNode constraint = JsonCustomMapper.get().createObjectNode();
        constraint.put("startTime", "1970-01-01T00:00:00Z+8:00");
        constraint.put("endTime", "4012-01-01T23:59:59Z+8:00");
        constraint.putArray("cron").add("0 0 0 ? * 1,2,3,4,5,6,7");
        return constraint;
    }

    // ═══ Assembly ═══

    /**
     * 构建 contents 节点：widgets + 空的 widgetGroups/widgetContainer
     */
    static ObjectNode buildContents(ArrayNode widgets) {
        ObjectNode contents = JsonCustomMapper.get().createObjectNode();
        contents.set("widgets", widgets);
        contents.putArray("widgetGroups");
        contents.putArray("widgetContainer");
        contents.put("enable", false);
        contents.put("zOrder", 0);
        contents.put("DuritionType", 0);
        contents.put("id", 0);
        contents.put("uuid", UUID.randomUUID().toString());
        return contents;
    }

    /**
     * 构建 widgetContainer
     */
    static ObjectNode buildContainer(String name, String wcType, int pcType,
                                     ObjectNode border, ObjectNode contents) {
        ObjectNode container = JsonCustomMapper.get().createObjectNode();
        container.put("id", CONTAINER_BASE_ID);
        container.put("uuid", UUID.randomUUID().toString());
        container.put("name", name);
        container.put("PCType", pcType);
        container.put("WCtype", wcType);
        container.put("zOrder", 1);
        container.put("pickPolicy", "ORDER");
        container.put("enable", true);
        container.set("winId", JsonCustomMapper.get().createObjectNode().put("value", 0));
        container.put("DuritionType", 0);
        container.put("audioGroup", "");
        container.set("layout", buildFullContainerLayout());
        container.set("border", border);
        container.set("contents", contents);
        return container;
    }

    /**
     * 将单个容器包装为完整页面 JSON（pageInfo + root）
     */
    static ObjectNode wrapPage(String programId, int pageId, ObjectNode container) {
        ObjectNode pageInfo = JsonCustomMapper.get().createObjectNode();
        pageInfo.put("name", "页面" + pageId);
        pageInfo.putArray("widgets");
        pageInfo.putArray("widgetGroups");
        pageInfo.putArray("widgetContainers").add(container);
        pageInfo.set("border", buildPageBorder());
        pageInfo.put("id", pageId);
        pageInfo.put("uuid", UUID.randomUUID().toString());

        ObjectNode json = JsonCustomMapper.get().createObjectNode();
        json.put("programID", Integer.parseInt(programId));
        json.put("pageID", pageId);
        json.set("pageInfo", pageInfo);
        return json;
    }

    /**
     * 将多个容器包装为完整页面 JSON。
     *
     * <p>容器按列表顺序放入 {@code widgetContainers} 数组，
     * 每个容器的 {@code zOrder/id/WCtype} 由调用方预先设置。</p>
     */
    static ObjectNode wrapPage(String programId, int pageId, List<ObjectNode> containers) {
        ObjectNode pageInfo = JsonCustomMapper.get().createObjectNode();
        pageInfo.put("name", "页面" + pageId);
        pageInfo.putArray("widgets");
        pageInfo.putArray("widgetGroups");
        ArrayNode containerArray = pageInfo.putArray("widgetContainers");
        for (ObjectNode c : containers) {
            containerArray.add(c);
        }
        pageInfo.set("border", buildPageBorder());
        pageInfo.put("id", pageId);
        pageInfo.put("uuid", UUID.randomUUID().toString());

        ObjectNode json = JsonCustomMapper.get().createObjectNode();
        json.put("programID", Integer.parseInt(programId));
        json.put("pageID", pageId);
        json.set("pageInfo", pageInfo);
        return json;
    }

    // ═══ Widget 工厂方法（供各 PageBuilder 复用） ═══

    /**
     * 构建 PICTURE 类型 widget。
     *
     * @param info     媒体文件信息
     * @param widgetId 全局 widget ID
     */
    static ObjectNode buildPictureWidget(MediaFileInfo info, int widgetId) {
        String ext = info.getFileName().contains(".")
                ? info.getFileName().substring(info.getFileName().lastIndexOf('.') + 1) : "";
        String dataSource = info.getMd5() + (ext.isEmpty() ? "" : "." + ext);

        ObjectNode widget = JsonCustomMapper.get().createObjectNode();
        widget.put("type", "PICTURE");
        widget.put("name", info.getFileName());
        widget.put("duration", info.getDuration());
        widget.put("repeatCount", 1);
        widget.put("displayRatio", "FULL");
        widget.put("id", widgetId);
        widget.put("uuid", UUID.randomUUID().toString());
        widget.put("filesize", info.getFileSize());
        widget.put("enable", true);
        widget.put("zOrder", 1);
        widget.put("backgroundColor", "#00000000");
        widget.put("backgroundDrawable", "");
        widget.put("backgroundMusic", "");
        widget.put("dataSource", dataSource);
        widget.put("originalDataSource", "./" + info.getFileName());
        widget.set("widgetId", JsonCustomMapper.get().createObjectNode().put("value", 0));
        widget.set("border", buildWidgetBorder());
        widget.set("layout", buildFullWidgetLayout());
        widget.set("inAnimation", buildNoAnimation());
        widget.set("outAnimation", buildNoAnimation());
        widget.set("constraints", JsonCustomMapper.get().createArrayNode()
                .add(buildDefaultConstraint()));
        return widget;
    }

    /**
     * 构建 VIDEO 类型 widget。
     *
     * @param info     媒体文件信息
     * @param widgetId 全局 widget ID
     */
    static ObjectNode buildVideoWidget(MediaFileInfo info, int widgetId) {
        ObjectNode widget = buildPictureWidget(info, widgetId);
        widget.put("type", "VIDEO");
        widget.set("metadata", JsonCustomMapper.get().createObjectNode().put("volume", 100));
        return widget;
    }
}
