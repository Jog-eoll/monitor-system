package com.publishgateway.udpproxy.assembly;

import com.alibaba.fastjson.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 已重组完成的文件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssembledFile {

    /** 文件查看接口使用的唯一ID。 */
    private String fileId;

    private String ruleId;

    private Long chainId;

    private String manufacturer;

    private String sourceIp;

    private Integer sourcePort;

    private String targetIp;

    private Integer targetPort;

    private String filePath;

    private String fileName;

    private String fileExtension;

    /** text/image/video/binary。 */
    private String contentType;

    @JsonIgnore
    @JSONField(serialize = false)
    private byte[] fileBytes;

    /** 重组文件在发布网关本地的存储路径。 */
    private String storagePath;

    private String sha256;

    private Boolean securePublishBlockPresent;

    private String securePublishSegmentType;

    private String securePublishPayloadSha256;

    private String securePublishStrippedPayloadSha256;

    private Boolean securePublishPayloadHashMatched;

    private String securePublishSignatureAlgorithm;

    private String securePublishSignature;

    private Long securePublishCreatedAt;

    private String securePublishError;

    private Boolean securePublishSigned;

    private Boolean securePublishVerified;

    private Boolean securePublishAllowed;

    private Boolean securePublishAuditOnly;

    private String securePublishVerifyMode;

    private String securePublishVerifyReason;

    private String securePublishKeyId;

    private Long securePublishSignTime;

    private Long securePublishExpireTime;

    private Integer totalPackets;

    private Integer totalSize;

    /** 青松 JetFile 源地址。 */
    private String protocolSourceAddr;

    /** 青松 JetFile 目的地址。 */
    private String protocolDestAddr;

    private Integer standardPayloadSize;

    private Long completedAt;

    private Long storedAt;
}
