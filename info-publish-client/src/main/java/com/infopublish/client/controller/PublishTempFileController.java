package com.infopublish.client.controller;

import com.infopublish.client.service.impl.PublishTempFileService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.nio.file.Files;

@RestController
@RequestMapping("/api/client/publish/v2/files")
public class PublishTempFileController {

    @Resource
    private PublishTempFileService tempFileService;

    @GetMapping("/{token}")
    public ResponseEntity<InputStreamResource> download(@PathVariable String token) throws Exception {
        PublishTempFileService.StoredFile stored = tempFileService.get(token);
        if (stored == null || stored.getPath() == null || !Files.exists(stored.getPath())) {
            return ResponseEntity.notFound().build();
        }
        String contentType = stored.getContentType() != null
                ? stored.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(stored.getSize())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + stored.getOriginalFilename().replace("\"", "_") + "\"")
                .body(new InputStreamResource(Files.newInputStream(stored.getPath())));
    }
}
