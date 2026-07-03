package com.infopublish.client.service.impl;

import com.infopublish.client.entity.dto.sigma.QingsongProgramResponse;
import com.infopublish.client.service.SigmaApiClient;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.Resource;
import java.util.Collections;

/**
 * 青松信发回调 API 客户端实现。
 */
@Slf4j
@Service
public class SigmaApiClientImpl implements SigmaApiClient {

    private static final String CLIENT_ID = "secure-publish-client";

    @Resource
    private RestTemplate restTemplate;

    @Override
    public QingsongProgramResponse.ProgramData getProgramByIp(String sigmaBaseUrl, String ip) {
        String url = UriComponentsBuilder
                .fromHttpUrl(normalizeBaseUrl(sigmaBaseUrl) + "/api/v1/callback/program-by-ip")
                .queryParam("ip", ip)
                .build(true)
                .toUriString();
        log.info("[青松客户端] 按 IP 获取节目单: ip={}, url={}", ip, url);

        try {
            HttpHeaders headers = buildHeaders(MediaType.APPLICATION_JSON);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            QingsongProgramResponse body = JSON.parseObject(resp.getBody(), QingsongProgramResponse.class);
            if (body == null || !body.isSuccessful()) {
                log.warn("[青松客户端] 获取节目单失败: ip={}, code={}, message={}, dataPresent={}",
                        ip,
                        body != null ? body.getCode() : null,
                        body != null ? body.getMessage() : null,
                        body != null && body.getData() != null);
                return null;
            }
            QingsongProgramResponse.ProgramData data = body.getData();
            log.info("[青松客户端] 获取节目单成功: ip={}, success={}, playlistId={}, targetIp={}, items={}",
                    ip,
                    data.getSuccess(),
                    data.getPlaylistId(),
                    data.getTarget() != null ? data.getTarget().getIp() : null,
                    data.getItems() != null ? data.getItems().size() : 0);
            return data;
        } catch (Exception e) {
            log.error("[青松客户端] 获取节目单异常: ip={}, error={}", ip, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public byte[] downloadFileContent(String sigmaBaseUrl, String playlistId, String innerFileId) {
        String url = normalizeBaseUrl(sigmaBaseUrl) + "/playlists/" + playlistId
                + "/files/" + innerFileId + "/content";
        log.info("[青松客户端] 下载文件内容: playlistId={}, innerFileId={}", playlistId, innerFileId);

        try {
            HttpHeaders headers = buildHeaders(MediaType.APPLICATION_OCTET_STREAM);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_OCTET_STREAM));
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<byte[]> resp = restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);
            byte[] body = resp.getBody();
            if (body == null || body.length == 0) {
                log.warn("[青松客户端] 下载文件内容为空: innerFileId={}", innerFileId);
                return null;
            }
            log.info("[青松客户端] 下载文件内容成功: innerFileId={}, size={}", innerFileId, body.length);
            return body;
        } catch (Exception e) {
            log.error("[青松客户端] 下载文件内容异常: innerFileId={}, error={}", innerFileId, e.getMessage(), e);
            return null;
        }
    }

    private HttpHeaders buildHeaders(MediaType contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("X-SP-Client-Id", CLIENT_ID);
        headers.set("X-SP-Request-Id", java.util.UUID.randomUUID().toString());
        headers.set("X-SP-Timestamp", java.time.Instant.now().toString());
        return headers;
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
