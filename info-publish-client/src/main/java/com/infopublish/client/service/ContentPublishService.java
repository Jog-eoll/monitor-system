package com.infopublish.client.service;

import com.infopublish.client.entity.dto.publish.ContentPublishRequest;
import com.infopublish.client.entity.dto.publish.ContentPublishResponse;

/**
 * 内容发布执行服务。
 */
public interface ContentPublishService {

    ContentPublishResponse publish(ContentPublishRequest request);
}
