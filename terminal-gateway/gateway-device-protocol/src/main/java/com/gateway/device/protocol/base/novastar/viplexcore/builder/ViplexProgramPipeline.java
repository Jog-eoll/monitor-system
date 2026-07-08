package com.gateway.device.protocol.base.novastar.viplexcore.builder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gateway.device.protocol.base.novastar.viplexcore.SdkFunction;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexCoreChannel;
import com.gateway.device.protocol.base.novastar.viplexcore.ViplexResponse;
import com.gateway.device.protocol.base.novastar.viplexcore.text.NovaViplexCoreTextStyle;
import com.gateway.device.protocol.common.GatewayTimeoutConstants;
import com.gateway.device.protocol.common.JsonCustomMapper;
import com.gateway.device.protocol.common.constant.MediaType;
import com.gateway.device.protocol.common.file.Thumbs;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * ViplexCore 节目管线 — SDK 调用步骤的封装。
 *
 * <p>封装创建节目、设置页面、制作节目、传输节目的完整管线，每个步骤通过
 * {@link ViplexCoreChannel#execute} 调用 SDK 并返回同步结果。</p>
 */
@Slf4j
public class ViplexProgramPipeline {

    private final ViplexCoreChannel channel;
    private final ViplexImagePageBuilder imagePageBuilder = new ViplexImagePageBuilder();
    private final ViplexTextPageBuilder textPageBuilder;
    private final ViplexVideoPageBuilder videoPageBuilder = new ViplexVideoPageBuilder();
    private final ViplexMediaPageBuilder mediaPageBuilder = new ViplexMediaPageBuilder();
    private final Duration timeout;

    public ViplexProgramPipeline(ViplexCoreChannel channel,
                                 ViplexTextPageBuilder textPageBuilder,
                                 Duration timeout) {
        this.channel = channel;
        this.textPageBuilder = textPageBuilder;
        this.timeout = timeout;
    }

    /**
     * 节目输出目录
     */
    public String getProgramOutputDir() throws IOException {
        Path dir = Paths.get(System.getProperty("java.io.tmpdir"), "viplex-programs");
        Files.createDirectories(dir);
        return dir.toString().replace('\\', '/');
    }

    /**
     * 从 CreateProgram 响应解析 programId
     */
    public String parseProgramId(ViplexResponse resp) {
        if (resp.getData() == null) return null;
        try {
            JsonNode root = JsonCustomMapper.get().readTree(resp.getData());
            JsonNode id = root.path("onSuccess").path("programID");
            return id.isMissingNode() ? null : id.asText();
        } catch (Exception e) {
            log.warn("解析 programId 失败: {}", resp.getData(), e);
            return null;
        }
    }

    /**
     * 创建节目
     */
    public ViplexResponse createProgram(int width, int height) {
        ObjectNode json = JsonCustomMapper.get().createObjectNode();
        json.put("name", "default");
        json.put("width", width);
        json.put("height", height);
        String jsonStr = json.toString();
        log.debug("[CreateProgram] json={}", jsonStr);
        ViplexResponse resp = channel.execute(SdkFunction.NV_CREATE_PROGRAM_ASYNC, jsonStr, timeout);
        log.debug("[CreateProgram] code={} data={}", resp.getCode(), resp.getData());
        return resp;
    }

    /**
     * 获取文件 MD5
     */
    public String getFileMd5(String filePath) {
        ObjectNode json = JsonCustomMapper.get().createObjectNode();
        json.put("filePath", filePath);
        String jsonStr = json.toString();
        log.debug("[GetFileMd5] filePath={}", filePath);
        ViplexResponse resp = channel.execute(SdkFunction.NV_GET_FILE_MD5_ASYNC, jsonStr, timeout);
        log.debug("[GetFileMd5] code={} data={}", resp.getCode(), resp.getData());
        if (!resp.isSuccess() || resp.getData() == null) return null;
        return resp.getData();
    }

    /**
     * 设置媒体页面（图片/视频）。
     * <p>图片委托到 {@link ViplexImagePageBuilder}，视频委托到 {@link ViplexVideoPageBuilder}。</p>
     */
    public ViplexResponse setMediaPage(String programId, int pageId,
                                       String filePath, String md5,
                                       String fileName, MediaType mediaType,
                                       int width, int height, int fileSize) {
        ObjectNode json;
        if (mediaType == MediaType.IMAGE) {
            json = imagePageBuilder.buildPage(programId, pageId,
                    Collections.singletonList(new ImageFileInfo(filePath, md5, fileName, fileSize)),
                    width, height);
        } else {
            json = videoPageBuilder.buildPage(programId, pageId, filePath, md5, fileName,
                    width, height, fileSize);
        }
        String jsonStr = json.toString();
        log.debug("[SetMediaPage] programId={} type={} json={}", programId, mediaType, jsonStr);
        ViplexResponse resp = channel.execute(SdkFunction.NV_SET_PAGE_PROGRAM_ASYNC, jsonStr, timeout);
        log.debug("[SetMediaPage] code={} data={}", resp.getCode(), resp.getData());
        return resp;
    }

    /**
     * 设置混合媒体页面 — 每个文件独立页面，对齐 NovaStar VP0001 节目结构。
     *
     * <p>使用 {@code nvSetPageProgramsAsync} 批量设置 N 个页面（每页 1 容器 1 widget）。
     * JSON 格式对齐 SDK 内部 {@code ProgramEditor::PageParams}：
     * {@code {"programID":x, "sceneItems":[{"page":pageInfo, "duration":>1000ms}, ...]}}
     * 超时 30s。</p>
     *
     * @param programId  节目 ID
     * @param mediaFiles 已按优先级排序的媒体文件信息列表
     */
    public ViplexResponse setMediaMultiPage(
            String programId,
            List<MediaFileInfo> mediaFiles) {
        List<ObjectNode> pages = mediaPageBuilder.buildPages(programId, 1, mediaFiles);

        // 构建 nvSetPageProgramsAsync 批量 JSON: sceneItems 格式
        ObjectNode json = JsonCustomMapper.get().createObjectNode();
        json.put("programID", Integer.parseInt(programId));
        ArrayNode sceneItems = json.putArray("sceneItems");
        for (ObjectNode page : pages) {
            ObjectNode item = JsonCustomMapper.get().createObjectNode();
            // 从 page 中提取 pageInfo 作为 sceneItem.page
            item.set("page", page.get("pageInfo"));
            // SDK 要求每个 sceneItem 的 duration > 1000ms
            item.put("duration", 10000);
            sceneItems.add(item);
        }
        String jsonStr = json.toString();
        log.debug("[SetMediaMultiPage] programId={} pageCount={} json={}", programId, pages.size(), jsonStr);
        Duration batchTimeout = Duration.ofMillis(GatewayTimeoutConstants.DEVICE_BATCH_PROGRAM_MS);
        ViplexResponse resp = channel.execute(SdkFunction.NV_SET_PAGE_PROGRAMS_ASYNC, jsonStr, batchTimeout);
        log.debug("[SetMediaMultiPage] code={} data={}", resp.getCode(), resp.getData());
        return resp;
    }

    /**
     * 设置文本页面（使用默认样式）
     */
    public ViplexResponse setTextPage(String programId, int pageId,
                                      String text, int width, int height) {
        return setTextPage(programId, pageId, text, width, height, null);
    }

    /**
     * 设置文本页面（可选样式覆盖）
     */
    public ViplexResponse setTextPage(String programId, int pageId,
                                      String text, int width, int height,
                                      NovaViplexCoreTextStyle styleOverride) {
        ObjectNode json = textPageBuilder.buildPage(programId, pageId, text, width, height, styleOverride);
        String jsonStr = json.toString();
        log.debug("[SetTextPage] programId={} json={}", programId, jsonStr);
        ViplexResponse resp = channel.execute(SdkFunction.NV_SET_PAGE_PROGRAM_ASYNC, jsonStr, timeout);
        log.debug("[SetTextPage] code={} data={}", resp.getCode(), resp.getData());
        return resp;
    }

    /**
     * 制作节目（生成协议文件到 outPutPath）
     */
    public ViplexResponse makeProgram(String programId, String outPutPath) {
        ObjectNode json = JsonCustomMapper.get().createObjectNode();
        json.put("programID", Integer.parseInt(programId));
        json.put("outPutPath", outPutPath + "/");
        String jsonStr = json.toString();
        log.debug("[MakeProgram] programId={} json={}", programId, jsonStr);
        ViplexResponse resp = channel.execute(SdkFunction.NV_MAKE_PROGRAM_ASYNC, jsonStr, timeout);
        log.debug("[MakeProgram] code={} data={}", resp.getCode(), resp.getData());
        return resp;
    }

    /**
     * 发送节目到终端
     */
    public ViplexResponse transferProgram(String sn, String programId,
                                          String outPutPath,
                                          Map<String, String> mediasPath) {
        // 写入默认缩略图 PNG 文件
        try {
            Path thumbPath = Paths.get(outPutPath, Thumbs.DEFAULT_THUMBNAIL_NAME);
            Files.write(thumbPath, Thumbs.DEFAULT_IMAGE_BYTES);
        } catch (IOException e) {
            log.warn("写入默认缩略图失败: {}", e.getMessage());
        }

        ObjectNode sendPaths = JsonCustomMapper.get().createObjectNode();
        sendPaths.put("programPath", outPutPath + "/program" + programId);
        ObjectNode medias = JsonCustomMapper.get().createObjectNode();
        for (Map.Entry<String, String> e : mediasPath.entrySet()) {
            medias.put(e.getKey(), e.getValue());
        }
        sendPaths.set("mediasPath", medias);

        String iconName = Thumbs.DEFAULT_THUMBNAIL_NAME;
        ObjectNode json = JsonCustomMapper.get().createObjectNode();
        json.put("sn", sn);
        json.put("iconPath", outPutPath + "/" + iconName);
        json.put("iconName", iconName);
        json.set("sendProgramFilePaths", sendPaths);
        json.put("programName", "default");
        json.put("deviceIdentifier", "default");
        json.put("startPlayAfterTransferred", true);
        json.put("insertPlay", true);
        String jsonStr = json.toString();
        log.debug("[TransferProgram] SN={} programId={} json={}", sn, programId, jsonStr);
        // 使用 executeWithProgress：SDK 传输期间多次进度回调，完成判定为 code=0 或 m_curBytes>=m_totalBytes
        ViplexResponse resp = channel.executeWithProgress(
                SdkFunction.NV_START_TRANSFER_PROGRAM_ASYNC, jsonStr, timeout,
                r -> {
                    if (r.getCode() == 0) return true;
                    if (r.getData() != null && r.getData().contains("m_totalBytes")) {
                        try {
                            JsonNode p = JsonCustomMapper.get().readTree(r.getData());
                            long cur = p.path("m_curBytes").asLong();
                            long total = p.path("m_totalBytes").asLong();
                            if (cur >= total) return true;
                            log.debug("[TransferProgram] 传输进度: {}/{} bytes", cur, total);
                            return false; // 忽略中间进度，继续等待
                        } catch (Exception e) {
                            log.warn("[TransferProgram] 解析传输进度失败: {}", r.getData());
                        }
                    }
                    return true; // 未知响应，结束等待
                });
        log.debug("[TransferProgram] code={} data={}", resp.getCode(), resp.getData());
        // 进度回调完成的（m_curBytes>=m_totalBytes），转为成功响应
        if (resp.getCode() != 0 && resp.getData() != null && resp.getData().contains("m_totalBytes")) {
            log.debug("[TransferProgram] 传输完成 (progress callback)");
            return new ViplexResponse(0, resp.getData());
        }
        return resp;
    }
}
