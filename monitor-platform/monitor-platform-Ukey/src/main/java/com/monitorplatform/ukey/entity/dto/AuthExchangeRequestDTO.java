package com.monitorplatform.ukey.entity.dto;

import lombok.Data;

/**
 * 客户端与认证服务端之间的认证报文 DTO
 */
@Data
public class AuthExchangeRequestDTO {

    /** 客户端认证 ID（一般为 cerId 或绑定 ID） */
    private String authId;

    /** 认证相关数据：
     *  - /auth/server/request 阶段：客户端生成的认证请求数据
     *  - /auth/server/verify 阶段：客户端生成的认证信息
     */
    private String requestData;

    /** 客户端证书内容（PEM），仅在 /verify 阶段需要，可选 */
    private String clientCert;
}
