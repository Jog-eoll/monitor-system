package com.infopublish.client.controller;

import com.infopublish.client.entity.dto.sigma.InfoBoardStatusResponse;
import com.infopublish.client.entity.dto.sigma.SigmaPublishStatusResponse;
import com.infopublish.client.entity.dto.sigma.SigmaVerifyRequest;
import com.infopublish.client.entity.dto.sigma.SigmaVerifyResponse;
import com.infopublish.client.entity.dto.precheck.PrecheckRequest;
import com.infopublish.client.entity.dto.precheck.PrecheckResponse;
import com.infopublish.client.entity.dto.publish.ContentPublishRequest;
import com.infopublish.client.entity.dto.publish.ContentPublishResponse;
import com.infopublish.client.service.ContentPublishService;
import com.infopublish.client.service.ContentPublishV2Service;
import com.infopublish.client.service.InfoBoardStatusService;
import com.infopublish.client.service.PublishPrecheckService;
import com.infopublish.client.service.PublishPrecheckV2Service;
import com.infopublish.client.service.SigmaPublishService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 信发平台安全发布控制器
 * <p>
 * 提供信发平台调用的安全发布接口。
 * 保留现有 {@code /api/publish/precheck} 兼容入口。
 * </p>
 *
 * <p>接口清单：</p>
 * <ul>
 *   <li>GET  /api/client/status — 客户端发布就绪状态（辅助查询）</li>
 *   <li>POST /api/client/publish/precheck — 发布前预检查 + 签发 publishPermit</li>
 *   <li>POST /api/client/publish/verify — 兼容旧版，单独签发 publishPermit</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/client")
public class SigmaPublishController {

    @Resource
    private SigmaPublishService sigmaPublishService;

    @Resource
    private PublishPrecheckService publishPrecheckService;

    @Resource
    private PublishPrecheckV2Service publishPrecheckV2Service;

    @Resource
    private ContentPublishService contentPublishService;

    @Resource
    private ContentPublishV2Service contentPublishV2Service;

    @Resource
    private InfoBoardStatusService infoBoardStatusService;

    /**
     * 客户端发布就绪状态
     */
    @GetMapping("/status")
    public SigmaPublishStatusResponse status() {
        log.info("[Sigma发布] 查询客户端状态");
        return sigmaPublishService.getStatus();
    }

    /**
     * 发布前预检查 + 签发 publishPermit
     */
    @GetMapping("/info-board/status")
    public InfoBoardStatusResponse infoBoardStatus(@RequestParam(value = "ip", required = false) String ip,
                                                   @RequestParam(value = "port", required = false) Integer port) {
        return infoBoardStatusService.getStatus(ip, port);
    }

    @PostMapping("/publish/precheck")
    public PrecheckResponse precheck(@Valid @RequestBody PrecheckRequest request) {
        log.info("[Sigma发布] precheck 转发: requestId={}", request.getRequestId());
        try {
            return publishPrecheckService.precheck(request);
        } catch (Exception e) {
            log.error("[Sigma发布] precheck 异常: requestId={}, error={}",
                    request.getRequestId(), e.getMessage(), e);
            return PrecheckResponse.error("precheck 接口异常: " + e.getMessage(), request.getRequestId());
        }
    }

    @PostMapping("/publish/precheckV2")
    public PrecheckResponse precheckV2(@Valid @RequestBody PrecheckRequest request) {
        log.info("[自研发布] precheckV2 转发: requestId={}", request.getRequestId());
        try {
            return publishPrecheckV2Service.precheckV2(request);
        } catch (Exception e) {
            log.error("[自研发布] precheckV2 异常: requestId={}, error={}",
                    request.getRequestId(), e.getMessage(), e);
            return PrecheckResponse.error("precheckV2 接口异常: " + e.getMessage(), request.getRequestId());
        }
    }

    /**
     * 兼容旧版：单独签发 publishPermit
     */
    @PostMapping("/publish/execute")
    public ContentPublishResponse publish(@Valid @RequestBody ContentPublishRequest request) {
        log.info("[Sigma publish] execute request: requestId={}", request.getRequestId());
        try {
            return contentPublishService.publish(request);
        } catch (Exception e) {
            log.error("[Sigma publish] execute error: requestId={}, error={}",
                    request.getRequestId(), e.getMessage(), e);
            return ContentPublishResponse.error(request.getRequestId(),
                    "PUBLISH_EXECUTE_FAILED", "publish execute 接口异常: " + e.getMessage());
        }
    }

    @PostMapping(value = "/publish/executeV2", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ContentPublishResponse publishV2(MultipartHttpServletRequest request) {
        String baseJson = null;
        try {
            baseJson = readPart(request, "base");
            String playlistJson = readPart(request, "playlist");
            MultipartFile[] files = extractPublishFiles(request);
            return contentPublishV2Service.publish(baseJson, playlistJson, files);
        } catch (Exception e) {
            log.error("[Sigma publish] executeV2 error: {}", e.getMessage(), e);
            return ContentPublishResponse.error(null,
                    "PUBLISH_EXECUTE_V2_FAILED", "publish executeV2 接口异常: " + e.getMessage());
        }
    }

    @PostMapping("/publish/verify")
    public SigmaVerifyResponse verify(@Valid @RequestBody SigmaVerifyRequest request) {
        log.info("[Sigma发布] verify 请求: requestId={}, playlistId={}",
                request.getRequestId(), request.getPlaylistId());
        try {
            SigmaVerifyResponse response = sigmaPublishService.verify(request);
            log.info("[Sigma发布] verify 完成: requestId={}, result={}",
                    request.getRequestId(), response.getResult());
            return response;
        } catch (Exception e) {
            log.error("[Sigma发布] verify 异常: requestId={}, error={}",
                    request.getRequestId(), e.getMessage(), e);
            return SigmaVerifyResponse.fail("verify 接口异常: " + e.getMessage(), request.getRequestId());
        }
    }

    private String readPart(MultipartHttpServletRequest request, String name) throws Exception {
        String parameter = request.getParameter(name);
        if (parameter != null && !parameter.trim().isEmpty()) {
            return parameter;
        }
        MultipartFile file = request.getFile(name);
        if (file == null || file.isEmpty()) {
            return null;
        }
        InputStream input = file.getInputStream();
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            input.close();
        }
    }

    private MultipartFile[] extractPublishFiles(MultipartHttpServletRequest request) {
        List<MultipartFile> files = new ArrayList<>();
        for (List<MultipartFile> partFiles : request.getMultiFileMap().values()) {
            for (MultipartFile file : partFiles) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                String name = file.getName();
                if ("base".equals(name) || "playlist".equals(name)) {
                    continue;
                }
                files.add(file);
            }
        }
        return files.toArray(new MultipartFile[0]);
    }
}
