package com.monitorplatform.ukey.entity.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 证书校验请求DTO
 */
@Data
public class CertValidateRequestDTO {

    /** 证书唯一编号（UKey中的cerId） */
    @NotBlank(message = "证书编号不能为空")
    private String certSerialNo;

    /** 证书内容 */
    private String certificateContent;

    /** 请求方客户端ID */
    @NotBlank(message = "客户端ID不能为空")
    private String clientId;

    /** 客户端IP */
    private String clientIp;

    /** 客户端MAC地址 */
    private String clientMac;
}
