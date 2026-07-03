package com.publishgateway.udpproxy.controller;

import com.publishgateway.udpproxy.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.PostConstruct;

@Slf4j
@RestController
public class DeviceProtocolMetadataProxyController {

    private RestTemplate restTemplate;

    @Value("${secure-delivery.terminal-gateway-url:http://127.0.0.1:8093}")
    private String terminalGatewayUrl;

    @Value("${secure-delivery.terminal-http.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${secure-delivery.terminal-http.read-timeout-ms:10000}")
    private int readTimeoutMs;

    @PostConstruct
    public void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        restTemplate = new RestTemplate(factory);
    }

    @GetMapping({
            "/api/secure-delivery/device-protocol/metadata",
            "/api/device-protocol/metadata"
    })
    public ResponseEntity<Object> metadata(@RequestParam(required = false) String vendor) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(normalizeBaseUrl(terminalGatewayUrl))
                .path("/api/device-protocol/metadata");
        if (vendor != null && !vendor.trim().isEmpty()) {
            builder.queryParam("vendor", vendor.trim());
        }
        String url = builder.build().toUriString();
        try {
            log.info("[DeviceProtocolMetadataProxy] forwarding metadata request: url={}", url);
            ResponseEntity<Object> response = restTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, Object.class);
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (ResourceAccessException e) {
            log.error("[DeviceProtocolMetadataProxy] terminal gateway unreachable: url={}, error={}", url, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Result.error(503, "TERMINAL_GATEWAY_UNREACHABLE: " + e.getMessage()));
        } catch (RestClientException e) {
            log.error("[DeviceProtocolMetadataProxy] terminal gateway metadata request failed: url={}, error={}",
                    url, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Result.error(502, "TERMINAL_GATEWAY_METADATA_FAILED: " + e.getMessage()));
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return "http://127.0.0.1:8093";
        }
        String value = baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
