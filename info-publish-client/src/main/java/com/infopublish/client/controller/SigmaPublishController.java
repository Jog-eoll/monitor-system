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
import com.infopublish.client.service.InfoBoardStatusService;
import com.infopublish.client.service.PublishPrecheckService;
import com.infopublish.client.service.SigmaPublishService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

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
    private ContentPublishService contentPublishService;

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
}
