package com.publishgateway.udpproxy.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * VAuthSDK 配置属性（发布加密网关）
 *
 * mock-mode=true  时使用 SimpleCryptoServiceImpl（AES模拟，无需硬件）
 * mock-mode=false 时使用 VAuthCryptoServiceImpl（国密SM4，需SDF卡或UKey）
 */
@Data
@Component
@ConfigurationProperties(prefix = "vauth")
public class VAuthConfig {

    /**
     * 是否使用Mock模式
     * true  = 使用AES模拟加密，无需SDF/UKey硬件，适合开发调试
     * false = 使用VAuthSDK真实国密加密，需要硬件设备
     */
    private Boolean mockMode = true;

    /**
     * 设备类型
     * sdf  = SDF密码卡
     * ukey = USB UKey（需符合GM/T 0016 SKF接口规范）
     */
    private String deviceType = "sdf";

    /**
     * 设备密码（SDF卡PIN或UKey PIN）
     */
    private String password = "";

    /**
     * 本端认证ID（发布网关在认证体系中的唯一标识）
     */
    private String authId = "";

    /**
     * UKey设备路径（来自 VAuth_ListUkeyInfos 的 path 字段）。
     * 仅建议兼容旧部署使用；生产推荐配置 ukeySn，并用 cerSn/cerId 做二次校验。
     */
    private String ukeyPath = "";

    /**
     * UKey设备序列号（来自 VAuth_ListUkeyInfos 的 sn 字段）
     */
    private String ukeySn = "";

    /**
     * UKey证书序列号（来自 VAuth_ListUkeyInfos 的 cerSn 字段）
     */
    private String ukeyCerSn = "";

    /**
     * UKey证书ID（来自 VAuth_ListUkeyInfos 的 cerId 字段）
     */
    private String ukeyCerId = "";

    /**
     * 认证服务端ID（管控平台的认证ID）
     */
    private String serverId = "";

    /**
     * 认证服务端证书文件路径（PEM格式）
     * 用于 VAuth_SetAuthServerInfo，让本端 SDK 校验管控平台的身份
     */
    private String serverCertPath = "certs/server.cer";

    /**
     * 本端UKey签名证书文件路径（PEM格式）
     * 在第二步握手 /auth/server/verify 中作为 clientCert 字段传给管控平台，
     * 管控平台调用 VAuth_ParseAuthInfo 时用此证书对本端数据验签
     */
    private String clientCertPath = "certs/client.cer";

    /**
     * 管控平台地址（用于双向认证握手）
     */
    @Value("${vauth.control-platform-url:http://127.0.0.1:8080}")
    private String controlPlatformUrl;

    /**
     * 加密时是否附带SM2签名
     * true = 接收方可验证发送方身份和数据完整性（推荐）
     * false = 仅加密，不签名
     */
    private Boolean signEnabled = true;

    /**
     * SVAC2 编码加密模式
     * true  = 使用 VAuth_EncryptPackData/DecryptPackData（SVAC2 编码格式，Linux 专用）
     * false = 使用 VAuth_EncryptData/DecryptData（国密整包加密，默认）
     * 注意：两端（publish-gateway 和 terminal-gateway）必须配置一致
     */
    private Boolean svacMode = false;

    /**
     * 是否在 VAuth 加解密诊断日志中输出明文字节十六进制预览。
     * 默认关闭，避免生产日志泄露业务载荷。
     */
    private Boolean payloadDebugLogEnabled = false;

    /**
     * 是否在启动后执行 VAuth_EncryptPackData 固定长度矩阵测试。
     * 仅用于隔离环境定位 SVAC PackData 输入边界，默认关闭。
     */
    private Boolean svacMatrixTestEnabled = false;
}
