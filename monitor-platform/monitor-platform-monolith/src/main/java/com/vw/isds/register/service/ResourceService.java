package com.vw.isds.register.service;


import com.vw.isds.register.entity.SysResource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 资源服务本地调用（替代原 Feign 客户端）
 * 原 Feign 调用 USER_SERVICE /upms/resource 路径
 */
@Slf4j
@Service
public class ResourceService {

    @Value("${monitor.role.url:http://localhost:8080}")
    private String roleServiceUrl;

    @Resource
    private RestTemplate restTemplate;

    public void updateResource(List<SysResource> resources) {
        String url = roleServiceUrl + "/upms/resource/updateResource";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<SysResource>> entity = new HttpEntity<>(resources, headers);
        restTemplate.postForEntity(url, entity, Map.class);
    }
}
