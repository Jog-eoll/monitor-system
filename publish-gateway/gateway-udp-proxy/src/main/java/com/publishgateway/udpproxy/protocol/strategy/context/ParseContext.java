package com.publishgateway.udpproxy.protocol.strategy.context;

import com.publishgateway.udpproxy.service.MinioUploadService;
import com.publishgateway.udpproxy.service.RelayFileSignatureService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 协议解析上下文
 * 封装解析过程中可能需要的辅助信息和服务引用
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParseContext {

    /** 规则ID */
    private String ruleId;

    /** 链路ID */
    private Long chainId;

    /** 数据来源IP */
    private String sourceIp;

    /** MinIO上传服务（图片上传用） */
    private MinioUploadService minioUploadService;

    private String boardIp;

    private Integer boardPort;

    private RelayFileSignatureService relayFileSignatureService;
}
