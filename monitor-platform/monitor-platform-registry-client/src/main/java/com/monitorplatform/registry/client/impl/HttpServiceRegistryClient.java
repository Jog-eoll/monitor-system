package com.monitorplatform.registry.client.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitorplatform.registry.client.ServiceRegistryClient;
import com.monitorplatform.registry.client.dto.ServiceInstance;
import com.monitorplatform.registry.client.dto.ServiceRegisterRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class HttpServiceRegistryClient implements ServiceRegistryClient {

    private final List<String> serverUrls;
    private final String apiPrefix;
    private final String registerPath;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;
    private final AtomicInteger currentIndex = new AtomicInteger(0);

    public HttpServiceRegistryClient(String serverAddr) {
        this(serverAddr, "/device/registry", "/auto-register");
    }

    public HttpServiceRegistryClient(String serverAddr, String apiPrefix, String registerPath) {
        this.serverUrls = Arrays.asList(serverAddr.split(","));
        this.apiPrefix = normalizePath(apiPrefix, "/device/registry");
        this.registerPath = normalizePath(registerPath, "/auto-register");
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClients.createDefault();
        log.info("init registry client, serverUrls={}, apiPrefix={}, registerPath={}",
                serverUrls, this.apiPrefix, this.registerPath);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseResult(String responseBody) throws Exception {
        return objectMapper.readValue(responseBody, Map.class);
    }

    private boolean isSuccess(Map<String, Object> result) {
        Object code = result == null ? null : result.get("code");
        return "200".equals(String.valueOf(code));
    }

    private String getCurrentServerUrl() {
        int index = currentIndex.getAndIncrement() % serverUrls.size();
        return serverUrls.get(index).trim();
    }

    private String buildUrl(String path) {
        String serverUrl = getCurrentServerUrl();
        String baseUrl = serverUrl.startsWith("http://") || serverUrl.startsWith("https://")
                ? serverUrl : "http://" + serverUrl;
        return baseUrl + apiPrefix + normalizePath(path, "");
    }

    private String normalizePath(String path, String defaultPath) {
        String value = path;
        if (value == null || value.trim().isEmpty()) {
            value = defaultPath;
        }
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        value = value.trim();
        return value.startsWith("/") ? value : "/" + value;
    }

    @Override
    public boolean register(ServiceRegisterRequest request) {
        try {
            String url = buildUrl(registerPath);
            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Content-Type", "application/json");

            String json = objectMapper.writeValueAsString(request);
            httpPost.setEntity(new StringEntity(json, StandardCharsets.UTF_8));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                log.debug("registry register response: status={}, body={}", statusCode, responseBody);
                if (statusCode == 200) {
                    Map<String, Object> result = parseResult(responseBody);
                    return isSuccess(result);
                }
                return false;
            }
        } catch (Exception e) {
            log.error("registry register failed", e);
            currentIndex.incrementAndGet();
            return false;
        }
    }

    @Override
    public boolean deregister(String instanceId) {
        try {
            String url = buildUrl("/deregister/" + instanceId);
            HttpDelete httpDelete = new HttpDelete(url);

            try (CloseableHttpResponse response = httpClient.execute(httpDelete)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                log.debug("registry deregister response: status={}, body={}", statusCode, responseBody);
                if (statusCode == 200) {
                    Map<String, Object> result = parseResult(responseBody);
                    return isSuccess(result);
                }
                return false;
            }
        } catch (Exception e) {
            log.error("registry deregister failed", e);
            currentIndex.incrementAndGet();
            return false;
        }
    }

    @Override
    public boolean heartbeat(String instanceId) {
        try {
            String url = buildUrl("/heartbeat/" + instanceId);
            HttpPost httpPost = new HttpPost(url);

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == 200) {
                    String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    Map<String, Object> result = parseResult(responseBody);
                    return isSuccess(result);
                }
                return false;
            }
        } catch (Exception e) {
            log.error("registry heartbeat failed", e);
            currentIndex.incrementAndGet();
            return false;
        }
    }

    @Override
    public List<ServiceInstance> discover(String serviceName) {
        try {
            String url = buildUrl("/discover/" + serviceName);
            HttpGet httpGet = new HttpGet(url);

            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                Map<String, Object> result = parseResult(responseBody);
                if (isSuccess(result) && result.get("data") != null) {
                    String dataJson = objectMapper.writeValueAsString(result.get("data"));
                    return objectMapper.readValue(dataJson, new TypeReference<List<ServiceInstance>>() {});
                }
                return null;
            }
        } catch (Exception e) {
            log.error("registry discover failed", e);
            currentIndex.incrementAndGet();
            return null;
        }
    }

    @Override
    public ServiceInstance getInstance(String instanceId) {
        try {
            String url = buildUrl("/instance/" + instanceId);
            HttpGet httpGet = new HttpGet(url);

            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                Map<String, Object> result = parseResult(responseBody);
                if (isSuccess(result) && result.get("data") != null) {
                    String dataJson = objectMapper.writeValueAsString(result.get("data"));
                    return objectMapper.readValue(dataJson, ServiceInstance.class);
                }
                return null;
            }
        } catch (Exception e) {
            log.error("registry get instance failed", e);
            currentIndex.incrementAndGet();
            return null;
        }
    }

    @Override
    public List<ServiceInstance> getAllServices() {
        try {
            String url = buildUrl("/services");
            HttpGet httpGet = new HttpGet(url);

            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                Map<String, Object> result = parseResult(responseBody);
                if (isSuccess(result) && result.get("data") != null) {
                    String dataJson = objectMapper.writeValueAsString(result.get("data"));
                    return objectMapper.readValue(dataJson, new TypeReference<List<ServiceInstance>>() {});
                }
                return null;
            }
        } catch (Exception e) {
            log.error("registry get all services failed", e);
            currentIndex.incrementAndGet();
            return null;
        }
    }

    public void close() {
        try {
            httpClient.close();
        } catch (Exception e) {
            log.error("close registry http client failed", e);
        }
    }
}
