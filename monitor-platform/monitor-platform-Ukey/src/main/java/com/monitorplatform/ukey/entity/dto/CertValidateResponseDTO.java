package com.monitorplatform.ukey.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 证书校验响应DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CertValidateResponseDTO {

    /** 是否通过校验 */
    private boolean valid;

    /** 错误码：0=通过, 1=未注册, 2=已注销, 3=已挂失, 4=已过期, 5=未生效, 6=算法不合规, 7=客户端不匹配 */
    private int errorCode;

    /** 错误消息 */
    private String message;

    /** 证书唯一编号 */
    private String certSerialNo;

    /** 绑定的客户端ID */
    private String boundClientId;

    public static CertValidateResponseDTO success(String certSerialNo, String boundClientId) {
        return new CertValidateResponseDTO(true, 0, "证书校验通过", certSerialNo, boundClientId);
    }

    public static CertValidateResponseDTO fail(int errorCode, String message) {
        return new CertValidateResponseDTO(false, errorCode, message, null, null);
    }
}
