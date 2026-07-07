package com.publishgateway.udpproxy.controller;

import com.publishgateway.udpproxy.assembly.AssembledFile;
import com.publishgateway.udpproxy.assembly.AssembledFileStore;
import com.publishgateway.udpproxy.common.Result;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * 重组文件查看接口。
 */
@RestController
@ConditionalOnProperty(name = "gateway.diagnostics.enabled", havingValue = "true")
@RequestMapping("/assembly/files")
public class AssembledFileController {

    @Resource
    private AssembledFileStore assembledFileStore;

    @GetMapping
    public Result<List<AssembledFile>> list(@RequestParam(required = false) String ruleId,
                                            @RequestParam(required = false) Long chainId,
                                            @RequestParam(defaultValue = "50") Integer limit) {
        int safeLimit = limit == null ? 50 : limit;
        return Result.success(assembledFileStore.list(ruleId, chainId, safeLimit));
    }

    @GetMapping("/{fileId}")
    public Result<AssembledFile> detail(@PathVariable String fileId) {
        AssembledFile file = assembledFileStore.get(fileId);
        if (file == null) {
            return Result.error(404, "重组文件不存在");
        }
        return Result.success(file);
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<FileSystemResource> download(@PathVariable String fileId) throws Exception {
        AssembledFile file = assembledFileStore.get(fileId);
        Path path = assembledFileStore.getContentPath(fileId);
        if (file == null || path == null) {
            return ResponseEntity.notFound().build();
        }

        FileSystemResource resource = new FileSystemResource(path.toFile());
        String fileName = file.getFileName() == null || file.getFileName().trim().isEmpty()
                ? fileId
                : file.getFileName();
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replace("+", "%20");
        MediaType mediaType = MediaTypeFactory.getMediaType(fileName)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(resource.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .body(resource);
    }
}
