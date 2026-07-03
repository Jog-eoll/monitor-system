package com.gateway.device.protocol.base.novastar.viplexcore.helper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ViplexCore SDK 文件 I/O 辅助 —— 静态工具类。
 */
public final class ViplexCoreFileHelper {

    private ViplexCoreFileHelper() {
    }

    /**
     * 将字节数据写入临时文件。
     */
    public static Path writeTempFile(byte[] data, String fileName) throws IOException {
        String ext = fileName != null && fileName.contains(".")
                ? fileName.substring(fileName.lastIndexOf('.')) : ".tmp";
        Path tmp = Files.createTempFile("viplex-", ext);
        Files.write(tmp, data);
        return tmp;
    }
}
