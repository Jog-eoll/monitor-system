package com.infopublish.client.service;

import com.infopublish.client.entity.dto.publish.ContentPublishResponse;
import org.springframework.web.multipart.MultipartFile;

public interface ContentPublishV2Service {

    ContentPublishResponse publish(String baseJson, String playlistJson, MultipartFile[] files);
}
