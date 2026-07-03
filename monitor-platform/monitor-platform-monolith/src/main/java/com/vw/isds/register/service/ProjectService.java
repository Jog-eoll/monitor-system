package com.vw.isds.register.service;


import com.vw.isds.register.entity.ProjectInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 项目服务本地调用（替代原 Feign 客户端）
 * 原 Feign 调用 USER_SERVICE /core/project 路径
 */
@Slf4j
@Service
public class ProjectService {

    @Value("${monitor.role.url:http://localhost:8080}")
    private String roleServiceUrl;

    @Resource
    private RestTemplate restTemplate;

    public void updateKey(HashMap<String, String> map) {
        String url = roleServiceUrl + "/core/project/updateKey";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<HashMap<String, String>> entity = new HttpEntity<>(map, headers);
        restTemplate.postForEntity(url, entity, Map.class);
    }

    public ProjectInfo info() {
        String url = roleServiceUrl + "/core/project/info";
        ResponseEntity<ProjectInfo> response = restTemplate.getForEntity(url, ProjectInfo.class);
        return response.getBody();
    }
}
