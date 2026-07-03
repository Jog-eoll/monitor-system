package com.publishgateway.udpproxy.service;





public interface TranscodeService {

    /**
     * 转码处理
     *
     * @param data     原始内容字节（文本或图片二进制）
     * @param dataType 内容类型：TEXT / IMAGE
     * @return 转码后的字节，透传模式直接返回原始 data
     */


    byte[] transcode(byte[] data,DataType dataType);


    enum DataType {
        TEXT, IMAGE
    }
}
