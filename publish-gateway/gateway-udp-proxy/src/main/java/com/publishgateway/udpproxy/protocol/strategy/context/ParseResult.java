package com.publishgateway.udpproxy.protocol.strategy.context;

import lombok.Data;

import java.util.List;

/**
 * 协议解析结果
 * 策略解析完成后返回此对象，包含解析后的结构化数据载体和元信息
 */
@Data
public class ParseResult {

    /** 是否解析成功（true=有内容需要上报，false=跳过） */
    private boolean success;

    /** 解析后的结构化上报载体 */
    private ReportPayload payload;

    /** 实际数据大小（用于上报日志，某些协议如文件传输的大小与原始包不同） */
    private int dataSize;

    /** 伴随上报载体列表（播放列表缓存命中时，包含列表中的图片/视频伴随文件） */
    private List<ReportPayload> additionalPayloads;

    /**
     * 解析成功，返回填充好的 payload
     */
    public static ParseResult success(ReportPayload payload, int dataSize) {
        ParseResult r = new ParseResult();
        r.success = true;
        r.payload = payload;
        r.dataSize = dataSize;
        return r;
    }

    /**
     * 解析成功，返回主 payload + 伴随文件 payloads（播放列表缓存命中场景）
     */
    public static ParseResult successBatch(ReportPayload payload, List<ReportPayload> additionalPayloads, int dataSize) {
        ParseResult r = new ParseResult();
        r.success = true;
        r.payload = payload;
        r.additionalPayloads = additionalPayloads;
        r.dataSize = dataSize;
        return r;
    }

    /**
     * 跳过上报（数据无需/无法解析）
     */
    public static ParseResult skip() {
        ParseResult r = new ParseResult();
        r.success = false;
        r.payload = null;
        r.dataSize = 0;
        return r;
    }
}
