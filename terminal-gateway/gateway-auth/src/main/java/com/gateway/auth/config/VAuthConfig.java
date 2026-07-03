package com.gateway.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * VAuthSDK 配置属性（加密/解密网关）
 *
 * <p>两个网关均部署于 Linux，使用 UKey 硬件，认证流程与监管程序客户端完全一致：
 * VAuth_OpenUkey(path, password, authId) + 三步握手双向认证
 *
 * <p>mock-mode=true  时使用 SimpleCryptoServiceImpl（AES模拟，无需硬件，用于开发调试）
 * <p>mock-mode=false 时使用 CryptoServiceImpl（VAuthSDK 国密 SM4+SM2，需 UKey 硬件）
 */
@Data
@Component
@ConfigurationProperties(prefix = "vauth")
public class VAuthConfig {

    /** 是否 Mock 模式（true=AES模拟，false=真实UKey国密） */
    private Boolean mockMode = true;

    /** 本端 UKey 的 authId（即 cerId 中 _ 之前的部分，如 "44030000003330000126"） */
    private String authId;

    /** UKey 设备路径，来自 VAuth_ListUkeyInfos 的 path 字段。 */
    private String ukeyPath = "";

    /** UKey 设备序列号，来自 VAuth_ListUkeyInfos 的 sn 字段。 */
    private String ukeySn = "";

    /** UKey 证书序列号，来自 VAuth_ListUkeyInfos 的 cerSn 字段。 */
    private String ukeyCerSn = "";

    /** UKey 证书 ID，来自 VAuth_ListUkeyInfos 的 cerId 字段。 */
    private String ukeyCerId = "";

    /** UKey PIN 码 */
    private String password;

    /** 管控平台地址，用于双向认证握手（如 http://192.168.1.233:8063） */
    private String controlPlatformUrl;

    /** 管控平台 UKey 的认证 ID（服务端 UKey cerId _ 之前的部分） */
    private String serverId;

    /** 管控平台签名证书路径（PEM格式），用于 VAuth_SetAuthServerInfo 校验服务端身份 */
    private String serverCertPath = "certs/server_SIGN.cer";

    /** 本端 UKey 签名证书路径（PEM格式），在 /auth/server/verify 的 clientCert 字段中传给管控平台验签 */
    private String clientCertPath = "certs/client_SIGN.cer";

    /** 加密/解密时是否附带 SM2 签名（建议 true，两端必须一致） */
    private Boolean signEnabled = true;

    /** SVAC2 编码加密模式（true=EncryptPackData/DecryptPackData，false=EncryptData/DecryptData，默认false） */
    private Boolean svacMode = false;
}
