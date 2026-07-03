package com.publishgateway.udpproxy.assembly;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UDP 文件重组结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileAssemblyResult {

    private FileAssemblyStatus status;

    private AssembledFile file;

    private String filePath;

    private String message;

    public boolean isCompleted() {
        return FileAssemblyStatus.COMPLETED.equals(status) && file != null;
    }

    public static FileAssemblyResult ignored(String message) {
        return FileAssemblyResult.builder()
                .status(FileAssemblyStatus.IGNORED)
                .message(message)
                .build();
    }

    public static FileAssemblyResult assembling(String filePath, String message) {
        return FileAssemblyResult.builder()
                .status(FileAssemblyStatus.ASSEMBLING)
                .filePath(filePath)
                .message(message)
                .build();
    }

    public static FileAssemblyResult completed(AssembledFile file) {
        return FileAssemblyResult.builder()
                .status(FileAssemblyStatus.COMPLETED)
                .file(file)
                .filePath(file != null ? file.getFilePath() : null)
                .message("FILE_ASSEMBLED")
                .build();
    }

    public static FileAssemblyResult discarded(String filePath, String message) {
        return FileAssemblyResult.builder()
                .status(FileAssemblyStatus.DISCARDED)
                .filePath(filePath)
                .message(message)
                .build();
    }

    public static FileAssemblyResult failed(String message) {
        return FileAssemblyResult.builder()
                .status(FileAssemblyStatus.FAILED)
                .message(message)
                .build();
    }
}
