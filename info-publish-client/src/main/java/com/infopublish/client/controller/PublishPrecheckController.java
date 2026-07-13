package com.infopublish.client.controller;

import com.infopublish.client.entity.dto.precheck.PrecheckRequest;
import com.infopublish.client.entity.dto.precheck.PrecheckResponse;
import com.infopublish.client.service.PublishPrecheckService;
import com.infopublish.client.service.PublishPrecheckV2Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

/**
 * 发布前预检查控制器
 * <p>
 * 提供信发平台调用的 precheck 接口。
 * </p>
 *
 * <p>接口清单：</p>
 * <ul>
 *     <li>POST /api/publish/precheck - 发布前预检查和发布许可签发</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/publish")
public class PublishPrecheckController {

    @Resource
    private PublishPrecheckService publishPrecheckService;

    @Resource
    private PublishPrecheckV2Service publishPrecheckV2Service;

    /**
     * 发布前预检查和发布许可签发
     * <p>
     * 信发平台在正式发布前调用此接口。该接口内部包含状态预检查、文件校验和 publishPermit 签发，
     * 信发平台必须等待该接口返回后再决定是否继续发布。
     * </p>
     *
     * @param request 预检查请求
     * @return 预检查结果，publishAllowed 标识是否允许继续发布
     */
    @PostMapping("/precheck")
    public PrecheckResponse precheck(@Valid @RequestBody PrecheckRequest request) {
        log.info("[预检查接口] 收到 precheck 请求: requestId={}, sigmaBaseUrl={}, playlistId={}",
                request.getRequestId(), request.getSigmaBaseUrl(), request.getPlaylistId());

        try {
            PrecheckResponse response = publishPrecheckService.precheck(request);
            log.info("[预检查接口] precheck 完成: requestId={}, publishAllowed={}, result={}",
                    request.getRequestId(), response.isPublishAllowed(), response.getResult());
            return response;
        } catch (Exception e) {
            log.error("[预检查接口] precheck 异常: requestId={}, error={}",
                    request.getRequestId(), e.getMessage(), e);
            return PrecheckResponse.error("预检查接口异常: " + e.getMessage(), request.getRequestId());
        }
    }

    @PostMapping("/precheckV2")
    public PrecheckResponse precheckV2(@Valid @RequestBody PrecheckRequest request) {
        log.info("[预检查接口] 收到 precheckV2 请求: requestId={}, playlistId={}",
                request.getRequestId(), request.getPlaylistId());

        try {
            PrecheckResponse response = publishPrecheckV2Service.precheckV2(request);
            log.info("[预检查接口] precheckV2 完成: requestId={}, publishAllowed={}, result={}",
                    request.getRequestId(), response.isPublishAllowed(), response.getResult());
            return response;
        } catch (Exception e) {
            log.error("[预检查接口] precheckV2 异常: requestId={}, error={}",
                    request.getRequestId(), e.getMessage(), e);
            return PrecheckResponse.error("precheckV2 接口异常: " + e.getMessage(), request.getRequestId());
        }
    }
}
