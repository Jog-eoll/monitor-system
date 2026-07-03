package com.infopublish.client.service;

import com.infopublish.client.entity.dto.sigma.QingsongProgramResponse;

/**
 * 青松信发回调 API 客户端。
 */
public interface SigmaApiClient {

    /**
     * 按情报板 IP 从青松回调服务获取当前节目单。
     *
     * @param sigmaBaseUrl 青松回调服务根地址
     * @param ip           情报板 IP
     * @return 青松节目单数据
     */
    QingsongProgramResponse.ProgramData getProgramByIp(String sigmaBaseUrl, String ip);

    /**
     * 兼容旧文件校验链路的文件下载方法。
     *
     * @param sigmaBaseUrl 青松回调服务根地址
     * @param playlistId   播放列表 ID
     * @param innerFileId  文件 ID
     * @return 文件字节内容
     */
    byte[] downloadFileContent(String sigmaBaseUrl, String playlistId, String innerFileId);
}
