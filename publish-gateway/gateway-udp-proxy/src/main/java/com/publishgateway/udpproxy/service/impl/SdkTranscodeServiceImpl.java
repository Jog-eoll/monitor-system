package com.publishgateway.udpproxy.service.impl;

import com.publishgateway.udpproxy.service.TranscodeService;

public class SdkTranscodeServiceImpl implements TranscodeService {
    @Override
    public byte[] transcode(byte[] data, DataType dataType) {
        // TODO: 接入外部转码SDK
        // 文本：调用 SDK 编码为目标格式
        // 图片：调用 SDK 转换图片格式
        // 当前抛出异常或降级透传，防止误开关
        return null;
    }
}
