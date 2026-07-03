package com.publishgateway.udpproxy.protocol.strategy.sigma;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 智能图片提取器
 * 
 * 从任意二进制数据中扫描并提取图片数据。
 * 无论图片被什么协议包裹，只要JPEG/PNG/GIF/BMP的魔数存在，就能提取出来。
 * 
 * 支持格式:
 * - JPEG: FF D8 FF ... FF D9
 * - PNG:  89 50 4E 47 0D 0A 1A 0A ... 49 45 4E 44
 * - GIF:  47 49 46 38
 * - BMP:  42 4D
 */
@Slf4j
public class ImageExtractor {

    /**
     * 从数据中查找并提取图片
     *
     * @param data 原始二进制数据（可以是完整UDP包、JetFileII payload等）
     * @return 提取结果，找不到则返回null
     */
    public static ImageResult extract(byte[] data) {
        if (data == null || data.length < 10) {
            return null;
        }

        // 按常用格式优先级依次扫描
        ImageResult result = findJPEG(data);
        if (result != null) return result;

        result = findPNG(data);
        if (result != null) return result;

        result = findGIF(data);
        if (result != null) return result;

        result = findBMP(data);
        if (result != null) return result;

        return null;
    }

    /**
     * 查找JPEG图片
     * 魔数: FF D8 FF (SOI + 第一个Marker)
     * 结束: FF D9 (EOI)
     */
    private static ImageResult findJPEG(byte[] data) {
        for (int i = 0; i <= data.length - 3; i++) {
            if (data[i] == (byte) 0xFF && data[i + 1] == (byte) 0xD8 && data[i + 2] == (byte) 0xFF) {
                // 找到JPEG起始位置，查找结束标记 FF D9
                int endIndex = findJPEGEnd(data, i + 3);

                int length = endIndex - i;
                if (length > 100) {
                    byte[] imageData = new byte[length];
                    System.arraycopy(data, i, imageData, 0, length);
                    log.info("【图片提取】找到JPEG: 偏移={}, 大小={}字节, 总数据={}字节",
                            i, length, data.length);
                    return new ImageResult("JPEG", imageData, i);
                }
            }
        }
        return null;
    }

    /**
     * 查找JPEG结束位置 (FF D9)
     */
    private static int findJPEGEnd(byte[] data, int startSearch) {
        for (int j = startSearch; j < data.length - 1; j++) {
            if (data[j] == (byte) 0xFF && data[j + 1] == (byte) 0xD9) {
                return j + 2; // 包含 FF D9
            }
        }
        // 没找到EOI，取到数据末尾（可能是分片传输，JPEG数据不完整）
        log.debug("【图片提取】JPEG未找到EOI结束标记，可能是分片数据");
        return data.length;
    }

    /**
     * 查找PNG图片
     * 魔数: 89 50 4E 47 0D 0A 1A 0A (8字节)
     * 结束: 49 45 4E 44 AE 42 60 82 (IEND chunk)
     */
    private static ImageResult findPNG(byte[] data) {
        for (int i = 0; i <= data.length - 8; i++) {
            if (data[i] == (byte) 0x89 && data[i + 1] == 0x50
                    && data[i + 2] == 0x4E && data[i + 3] == 0x47
                    && data[i + 4] == 0x0D && data[i + 5] == 0x0A
                    && data[i + 6] == 0x1A && data[i + 7] == 0x0A) {
                // 找到PNG起始位置，查找IEND chunk
                int endIndex = findPNGEnd(data, i + 8);

                int length = endIndex - i;
                if (length > 100) {
                    byte[] imageData = new byte[length];
                    System.arraycopy(data, i, imageData, 0, length);
                    log.info("【图片提取】找到PNG: 偏移={}, 大小={}字节, 总数据={}字节",
                            i, length, data.length);
                    return new ImageResult("PNG", imageData, i);
                }
            }
        }
        return null;
    }

    /**
     * 查找PNG结束位置 (IEND chunk: 49 45 4E 44)
     */
    private static int findPNGEnd(byte[] data, int startSearch) {
        for (int j = startSearch; j < data.length - 7; j++) {
            if (data[j] == 0x49 && data[j + 1] == 0x45
                    && data[j + 2] == 0x4E && data[j + 3] == 0x44
                    && data[j + 4] == (byte) 0xAE && data[j + 5] == 0x42
                    && data[j + 6] == 0x60 && data[j + 7] == (byte) 0x82) {
                return j + 8; // 包含IEND + CRC
            }
        }
        return data.length;
    }

    /**
     * 查找GIF图片
     * 魔数: 47 49 46 38 (GIF8)
     * 结束: 3B (GIF trailer)
     */
    private static ImageResult findGIF(byte[] data) {
        for (int i = 0; i <= data.length - 6; i++) {
            if (data[i] == 0x47 && data[i + 1] == 0x49
                    && data[i + 2] == 0x46 && data[i + 3] == 0x38) {
                // GIF87a or GIF89a
                byte version = data[i + 4];
                if (version == 0x37 || version == 0x39) {
                    // 查找GIF trailer 0x3B
                    int endIndex = data.length;
                    for (int j = i + 6; j < data.length; j++) {
                        if (data[j] == 0x3B) {
                            endIndex = j + 1;
                            break;
                        }
                    }

                    int length = endIndex - i;
                    if (length > 100) {
                        byte[] imageData = new byte[length];
                        System.arraycopy(data, i, imageData, 0, length);
                        log.info("【图片提取】找到GIF: 偏移={}, 大小={}字节", i, length);
                        return new ImageResult("GIF", imageData, i);
                    }
                }
            }
        }
        return null;
    }

    /**
     * 查找BMP图片
     * 魔数: 42 4D (BM)
     * BMP头部包含文件大小信息
     */
    private static ImageResult findBMP(byte[] data) {
        for (int i = 0; i <= data.length - 14; i++) {
            if (data[i] == 0x42 && data[i + 1] == 0x4D) {
                // 读取BMP文件大小 (偏移2-5, 4字节小端序)
                int fileSize = ((data[i + 5] & 0xFF) << 24)
                        | ((data[i + 4] & 0xFF) << 16)
                        | ((data[i + 3] & 0xFF) << 8)
                        | (data[i + 2] & 0xFF);

                // 验证文件大小合理性
                if (fileSize > 100 && fileSize <= (data.length - i) && fileSize < 50 * 1024 * 1024) {
                    byte[] imageData = new byte[fileSize];
                    System.arraycopy(data, i, imageData, 0, fileSize);
                    log.info("【图片提取】找到BMP: 偏移={}, 大小={}字节", i, fileSize);
                    return new ImageResult("BMP", imageData, i);
                }
            }
        }
        return null;
    }

    /**
     * 图片提取结果
     */
    @Data
    public static class ImageResult {
        /** 图片格式: JPEG/PNG/GIF/BMP */
        private final String format;
        /** 图片二进制数据 */
        private final byte[] imageData;
        /** 图片在原始数据中的偏移位置 */
        private final int offset;

        public ImageResult(String format, byte[] imageData, int offset) {
            this.format = format;
            this.imageData = imageData;
            this.offset = offset;
        }
    }
}
