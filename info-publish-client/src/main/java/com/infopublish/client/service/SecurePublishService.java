package com.infopublish.client.service;

import com.infopublish.client.entity.dto.SecurePublishItem;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface SecurePublishService {

    Map<String, Object> getStatus();

    List<SecurePublishItem> listItems();

    Map<String, Object> scanNow();

    Map<String, Object> createTestPackage(String payloadPath, String outputDir);

    Map<String, Object> createTestKeyPair(boolean overwrite);

    Map<String, Object> getSignerStatus();

    Map<String, Object> createSignerKeyPair(boolean overwrite);

    Map<String, Object> signPackage(String payloadPath, String outputDir);

    Map<String, Object> signUploadedFiles(MultipartFile[] files, String[] relativePaths, String outputDir);

    Map<String, Object> scanSignerInput();
}
