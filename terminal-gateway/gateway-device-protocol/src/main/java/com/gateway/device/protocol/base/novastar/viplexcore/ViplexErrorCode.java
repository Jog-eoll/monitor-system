package com.gateway.device.protocol.base.novastar.viplexcore;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * ViplexCore SDK 错误码枚举 — 映射 {@code viplexerror.h} ViplexError::uint16_t。
 *
 * <p>所有 {@code nv*Async} 回调中的非零 code 均可通过
 * {@link #describe(int)} 获取人可读的 name + 中文描述。</p>
 */
@Getter
@AllArgsConstructor
public enum ViplexErrorCode {

    // ── 基础错误 (0x00–0x19) ──
    OK(0x00, "成功"),
    ERR_COMMON(0x01, "通用错误"),
    ERR_NULL(0x02, "参数为空"),
    ERR_INVALID_PARAM(0x03, "无效参数"),
    ERR_TIMEOUT(0x04, "超时"),
    ERR_IO_EXCEPTION(0x05, "IO 异常"),
    ERR_INTERRUPTED(0x06, "中断异常"),
    ERR_INCOMPLETED(0x07, "未完成"),
    ERR_INCOMPLETED_SET(0x08, "未完成的设置异常"),
    ERR_ALREADY_DONE(0x09, "已经执行异常"),
    ERR_SECURITY(0x0A, "安全问题"),
    ERR_PERMISSION_DENIED(0x0B, "权限不够（授权失败）"),
    ERR_NOT_IMPLEMENTED(0x0C, "未实现的功能"),
    ERR_REMOTE_EXCEPTION(0x0D, "远程过程调用异常"),
    ERR_UNSUPPORTEDENCODING(0x0F, "不支持的编码异常"),
    ERR_JSON_EXCEPTION(0x10, "Json 异常"),
    ERR_FORBIDDEN(0x11, "禁止访问"),
    ERR_NO_SESSION(0x12, "无会话连接异常"),
    ERR_NOT_EXISTED(0x13, "不存在的异常"),
    ERR_NO_SPACE(0x14, "没有空间的异常"),
    ERR_DATABASE_EXCEPTION(0x15, "数据库异常"),
    ERR_TOO_FREQUENTLY(0x16, "操作过于频繁"),
    ERR_ALREADY_EXISTED(0x17, "已经存在"),
    ERR_VERIFY_FAILED(0x18, "校验失败（如 MD5 不一致）"),
    ERR_FILE_ILLEGAL(0x19, "升级包不合法或错误"),

    // ── 签名 / 账号 / 硬件 (0x20–0x25) ──
    ERR_SIGNATURE_NO_MATCH(0x20, "签名不匹配（升级包）"),
    ERR_ACCOUNT_NOT_EXIST(0x21, "账号不存在"),
    ERR_SCREEN_NOT_CONFIG(0x22, "未配屏"),
    ERR_NETWORK(0x23, "网络异常"),
    ERR_UNSUPPORTED(0x24, "终端不支持"),
    ERR_LORA_SLAVE_UNSUPPORTED(0x25, "射频从设备不支持（如给从设备设置音量/亮度/时间等）"),

    // FIXME [nvSetScreenBrightnessAsync]由此请求总结的非官方异常CODE
    ERR_SCREEN_NOT_CONNECTED(0x26, "[UNOFFICIAL]显示屏未连接"),

    // ── 升级相关 (0x33–0x36) ──
    ERR_NOT_ONE_FPGA(0x33, "升级时通过产品和平台验证的 FPGA 个数不是一个"),
    ERR_NOT_SUPPORT_PRODUCT(0x34, "升级校验时，终端产品不支持"),
    ERR_VERSION_LOW(0x35, "待升级软件版本低于当前终端已安装版本"),
    ERR_NOT_SUPPORT_PLATFORM(0x36, "升级校验时，终端平台不支持"),

    // ── 登录锁定 ──
    ERR_LOGIN_LOCKED(0x43, "密码连续错误 3 次，登录锁定"),

    // ── 命名 / 回调 / 数据库 (0xFF04–0xFF08) ──
    NAME_ALREADY_EXIST(0xFF04, "NTP 对时服务器或绑定服务器重命名"),
    CALLBACK_IS_NULL(0xFF05, "回调为空"),
    LOG_INIT_ERR(0xFF06, "日志初始化错误"),
    DB_INIT_ERR(0xFF07, "数据库初始化错误"),
    VALUE_RANGE_ERROR(0xFF08, "值越界"),

    // ── TCP 连接 (0xFF11–0xFF1e) ──
    CONNECTION_SUCCESS(0xFF11, "TCP 连接成功"),
    ALREADY_CONNECTION(0xFF12, "TCP 已经建立了连接"),
    CONNECTION_REFUSED_ERROR(0xFF13, "连接被拒绝"),
    REMOTE_HOST_CLOSED_ERROR(0xFF14, "远程主机关闭连接"),
    HOST_NOT_FOUND_ERROR(0xFF15, "主机未找到"),
    ERR_UDP_ADDRINUSE(0xFF16, "UDP 监听端口被占用"),
    ERR_UDP_BINDFAIL(0xFF17, "UDP 监听失败"),
    SOCKET_TIMEOUT_ERROR(0xFF18, "Socket 超时"),
    NETWORK_ERROR(0xFF1A, "网络错误"),
    SOCKET_ADDRESS_NOT_AVAILABLE_ERROR(0xFF1C, "Socket 地址不可用"),
    UNKNOWN_SOCKET_ERROR(0xFF1D, "未知 Socket 错误"),
    SET_BIND_PLAYER_ERROR(0xFF1E, "绑定播放器错误"),
    UDP_SEARCH_GATEWAY_IS_NULL_ERROR(0xFF1F, "网关地址为空"),

    // ── SN / 路径 / 文件 (0xFF20–0xFF3b) ──
    LOGIN_SN_IS_ILLEGAL(0xFF20, "登录 SN 不合法"),
    SN_IS_ILLEGAL(0xFF21, "SN 不合法"),
    ERR_PATH_NOT_EXIST(0xFF22, "路径不存在"),
    ERR_FILE_NUZIP_FAILED(0xFF23, "文件解压失败"),
    ERR_DESCRIPTION_JSON_NULL(0xFF24, "描述 JSON 为空"),
    PARAM_IS_EXCEPTION(0xFF25, "参数异常"),
    NO_MATCH_FILE(0xFF27, "没有匹配的文件"),
    UPDATE_PACKAGE_UPLOADING(0xFF28, "升级包上传中"),
    START_UPDATE(0xFF29, "升级包上传完毕"),
    DIR_NOT_EXIST(0xFF30, "路径不存在"),
    CREATE_JSONFILE_FAILED(0xFF3A, "创建 JSON 文件失败"),
    UPLOAD_FILE(0xFF3B, "正在上传文件"),

    // ── 节目 / 存储 (0xFF3c–0xFF4f) ──
    CREATEPROGRAM_ERROR(0xFF3C, "创建节目错误"),
    GETPROGRAM_ERROR(0xFF3D, "获取节目错误"),
    PROGRAMTEMPLATE_DATABASE_EMPTY(0xFF3E, "数据库读取节目模板内容为空"),
    ERR_CHANGEPWD_OLDPWDERR(0xFF3F, "旧密码错误"),
    ERR_UPDATEPACKAGE_TRANSPORT(0xFF40, "安装包发送失败"),
    TEMPLATEID_IS_ERROR(0xFF41, "模板 ID 错误"),
    DISCONNECT_SUCCESS(0xFF42, "断开连接成功"),
    ERR_INSERTERROR(0xFF43, "插入数据库失败"),
    ERR_DELETEERROR(0xFF44, "删除数据库失败"),
    ERR_TCP_UNCONNECTED(0xFF45, "TCP Socket 未连接"),
    ERR_NOT_LOGIN(0xFF46, "未登录"),
    ERR_PWDERROR_CLEARPASSWORD(0xFF47, "用户名密码错误（已清除记住的密码）"),
    OLD_DATABASE_ISEMPTY(0xFF48, "旧数据库为空"),
    ERR_ALREADY_LOGIN(0xFF49, "已登录，不允许二次登录"),
    ERR_LOGIN_FAIL(0xFF50, "登录失败"),

    // ── 固件 / 媒体 / FTP (0xFF51–0xFF5D) ──
    ERR_UNGETFIRMWARE_VERSIONISNULL(0xFF51, "未获取固件版本信息，无法做旧卡兼容配置"),
    MEDIA_UPLOADING(0xFF52, "媒体正在上传"),
    ERR_FTP_SEND_ERROR(0xFF53, "FTP 发送失败，网络断开"),
    ERR_VERISON_ROLLBACK(0xFF54, "升级版本回退错误"),
    ERR_NOT_SUPPORT_VERIFY(0xFF55, "不支持版本升级验证"),
    ERR_FEEDBACK_FAILED(0xFF56, "意见反馈上传文件失败"),
    NO_AVAILABLE_SPACE(0xFF57, "Tcard 存储空间不足"),
    USB_NO_AVAILABLE_SPACE(0xFF58, "U 盘空间不足"),
    UPDATE_PACKAGE_DOWNLOADING(0xFF59, "升级包下载中"),
    ERR_GET_ONLINE_PACKAGE_FAILED(0xFF5A, "获取线上升级包失败"),
    ERR_STOP_DOWNLOAD_UPDATE_PACKAGE(0xFF5B, "终止升级包下载"),
    ERR_DOWNLOAD_UPDATE_PACKAGE_FAILED(0xFF5C, "升级包下载失败"),
    ERR_LOCAL_FILE(0xFF5D, "本地文件打开失败"),
    ERR_CURL_INIT(0xFF5E, "Curl 初始化失败"),
    ERR_FILE_VERIFY_IFAILED(0xFF5F, "升级包校验失败"),

    // ── 网络检测 / 绑定 / 日志 (0xFF60–) ──
    ERR_NETWORKCONNECT(0xFF60, "网络检测异常"),
    ERR_USERNAME_EMPTY(0xFF61, "绑定时用户名为空"),
    ERR_PASSWORD_EMPTY(0xFF62, "绑定时密码为空"),
    ERR_RESPONSEDATA_ERROR(0xFF63, "终端返回参数异常"),
    ERR_CANCEL_PROGRESS(0xFF64, "取消压缩过程"),
    ERR_TERMINALLOG_DOWNLOAD_FINISH(0xFF65, "终端日志下载压缩完成"),
    ERR_GET_TOKEN_ERROR(0xFF66, "HTTP 请求 Token 值错误"),
    ERR_GET_OSSINFO_ERROR(0xFF67, "HTTP 请求 OSS 认证信息值错误"),
    ERR_CANCEL_TERMINALLOG_UPLOAD(0xFF68, "取消日志上传"),
    ERR_GET_OSSLINK_ERROR(0xFF69, "HTTP 请求下载日志链接错误"),
    ERR_GET_OSSLINK_SUCCESS(0xFF6A, "HTTP 请求下载日志链接成功"),
    ERR_TERMINAL_UPLOAD_FINISH(0xFF6B, "终端日志上传完成"),
    ERR_CREATE_DIR_ERROR(0xFF6C, "创建文件夹失败"),
    ERR_BOT_PUBLIC_NETWORK(0xFF6D, "不是公网模式"),
    ERR_PROGRAM_OUT_OF_SIZE(0xFF6E, "节目超出尺寸"),

    // ── Curl 错误 (0x07E5+) ──
    ERR_CURLE_UNSUPPORTED_PROTOCOL(0x07E5, "Curl: 不支持的协议"),
    ERR_CURLE_FAILED_INIT(0x07E6, "Curl: 初始化失败"),
    ERR_CURLE_URL_MALFORMAT(0x07E7, "Curl: URL 格式错误"),
    ERR_CURLE_COULDNT_RESOLVE_PROXY(0x07E9, "Curl: 无法解析代理"),
    ERR_CURLE_COULDNT_RESOLVE_HOST(0x07EA, "Curl: 无法解析主机"),
    ERR_CURLE_COULDNT_CONNECT(0x07EB, "Curl: 无法连接"),
    ERR_CURLE_FTP_WEIRD_SERVER_REPLY(0x07EC, "Curl: FTP 服务器异常回复"),
    ERR_CURLE_REMOTE_ACCESS_DENIED(0x07ED, "Curl: 远程访问被拒绝"),
    ERR_CURLE_FTP_WEIRD_PASS_REPLY(0x07EF, "Curl: FTP PASS 异常回复"),
    ERR_CURLE_FTP_WEIRD_PASV_REPLY(0x07F1, "Curl: FTP PASV 异常回复"),
    ERR_CURLE_FTP_WEIRD_227_FORMAT(0x07F2, "Curl: FTP 227 格式异常"),
    ERR_CURLE_FTP_CANT_GET_HOST(0x07F3, "Curl: FTP 无法获取主机"),
    ERR_CURLE_FTP_COULDNT_SET_TYPE(0x07F5, "Curl: FTP 无法设置类型"),
    ERR_CURLE_PARTIAL_FILE(0x07F6, "Curl: 部分文件"),
    ERR_CURLE_FTP_COULDNT_RETR_FILE(0x07F7, "Curl: FTP 无法获取文件"),
    ERR_CURLE_QUOTE_ERROR(0x07F9, "Curl: Quote 命令失败"),
    ERR_CURLE_HTTP_RETURNED_ERROR(0x07FA, "Curl: HTTP 返回错误"),
    ERR_CURLE_WRITE_ERROR(0x07FB, "Curl: 写入错误"),
    ERR_CURLE_UPLOAD_FAILED(0x07FD, "Curl: 上传失败"),
    ERR_CURLE_READ_ERROR(0x07FE, "Curl: 读取错误"),
    ERR_CURLE_OUT_OF_MEMORY(0x07FF, "Curl: 内存不足"),
    ERR_CURLE_OPERATION_TIMEDOUT(0x0800, "Curl: 操作超时"),
    ERR_CURLE_FTP_PORT_FAILED(0x0802, "Curl: FTP PORT 失败"),
    ERR_CURLE_FTP_COULDNT_USE_REST(0x0803, "Curl: FTP REST 失败"),
    ERR_CURLE_RANGE_ERROR(0x0805, "Curl: Range 命令失败"),
    ERR_CURLE_HTTP_POST_ERROR(0x0806, "Curl: HTTP POST 错误"),
    ERR_CURLE_SSL_CONNECT_ERROR(0x0807, "Curl: SSL 连接错误"),
    ERR_CURLE_BAD_DOWNLOAD_RESUME(0x0808, "Curl: 下载续传失败"),
    ERR_CURLE_FILE_COULDNT_READ_FILE(0x0809, "Curl: 无法读取文件"),
    ERR_CURLE_LDAP_CANNOT_BIND(0x080A, "Curl: LDAP 无法绑定"),
    ERR_CURLE_LDAP_SEARCH_FAILED(0x080B, "Curl: LDAP 搜索失败"),
    ERR_CURLE_FUNCTION_NOT_FOUND(0x080D, "Curl: 函数未找到"),
    ERR_CURLE_ABORTED_BY_CALLBACK(0x080E, "Curl: 被回调中止"),
    ERR_CURLE_BAD_FUNCTION_ARGUMENT(0x080F, "Curl: 函数参数错误"),
    ERR_CURLE_INTERFACE_FAILED(0x0811, "Curl: 接口失败"),
    ERR_CURLE_TOO_MANY_REDIRECTS(0x0813, "Curl: 重定向过多"),
    ERR_CURLE_UNKNOWN_TELNET_OPTION(0x0814, "Curl: 未知 Telnet 选项"),
    ERR_CURLE_TELNET_OPTION_SYNTAX(0x0815, "Curl: Telnet 选项语法错误"),
    ERR_CURLE_PEER_FAILED_VERIFICATION(0x0817, "Curl: 对端证书验证失败"),
    ERR_CURLE_GOT_NOTHING(0x0818, "Curl: 无响应"),
    ERR_CURLE_SSL_ENGINE_NOTFOUND(0x0819, "Curl: SSL 引擎未找到"),
    ERR_CURLE_SSL_ENGINE_SETFAILED(0x081A, "Curl: SSL 引擎设置失败"),
    ERR_CURLE_SEND_ERROR(0x081B, "Curl: 发送失败"),
    ERR_CURLE_RECV_ERROR(0x081C, "Curl: 接收失败"),
    ERR_CURLE_SSL_CERTPROBLEM(0x081E, "Curl: 本地证书问题"),
    ERR_CURLE_SSL_CIPHER(0x081F, "Curl: 无法使用指定密码"),
    ERR_CURLE_SSL_CACERT(0x0820, "Curl: CA 证书问题"),
    ERR_CURLE_BAD_CONTENT_ENCODING(0x0821, "Curl: 无法识别的传输编码"),
    ERR_CURLE_LDAP_INVALID_URL(0x0822, "Curl: LDAP URL 无效"),
    ERR_CURLE_FILESIZE_EXCEEDED(0x0823, "Curl: 超过最大文件大小"),
    ERR_CURLE_USE_SSL_FAILED(0x0824, "Curl: FTP SSL 级别失败"),
    ERR_CURLE_SEND_FAIL_REWIND(0x0825, "Curl: 发送回退失败"),
    ERR_CURLE_SSL_ENGINE_INITFAILED(0x0826, "Curl: SSL 引擎初始化失败"),
    ERR_CURLE_LOGIN_DENIED(0x0827, "Curl: 登录被拒绝"),
    ERR_CURLE_TFTP_NOTFOUND(0x0828, "Curl: TFTP 文件未找到"),
    ERR_CURLE_TFTP_PERM(0x0829, "Curl: TFTP 权限问题"),
    ERR_CURLE_REMOTE_DISK_FULL(0x082A, "Curl: 远程磁盘已满"),
    ERR_CURLE_TFTP_ILLEGAL(0x082B, "Curl: 非法 TFTP 操作"),
    ERR_CURLE_TFTP_UNKNOWNID(0x082C, "Curl: 未知传输 ID"),
    ERR_CURLE_REMOTE_FILE_EXISTS(0x082D, "Curl: 远程文件已存在"),
    ERR_CURLE_TFTP_NOSUCHUSER(0x082E, "Curl: 无此用户"),
    ERR_CURLE_CONV_FAILED(0x082F, "Curl: 转换失败"),
    ERR_CURLE_CONV_REQD(0x0830, "Curl: 需要注册转换回调"),
    ERR_CURLE_SSL_CACERT_BADFILE(0x0831, "Curl: CACERT 文件无法加载"),
    ERR_CURLE_REMOTE_FILE_NOT_FOUND(0x0832, "Curl: 远程文件未找到"),
    ERR_CURLE_SSH(0x0833, "Curl: SSH 层错误"),
    ERR_CURLE_SSL_SHUTDOWN_FAILED(0x0834, "Curl: SSL 关闭失败"),
    ERR_CURLE_AGAIN(0x0835, "Curl: Socket 未就绪，稍后重试"),
    ERR_CURLE_SSL_CRL_BADFILE(0x0836, "Curl: CRL 文件无法加载"),
    ERR_CURLE_SSL_ISSUER_ERROR(0x0837, "Curl: Issuer 检查失败"),

    // ── 其他 ──
    FTP_LOCAL_FILE_NOT_EXIST(0xFF71, "FTP 本地文件不存在"),
    ERR_FEEDBACK_STOP(0xFF72, "取消上传意见反馈"),
    ERR_FEEDBACK_TIMEOUT(0xFF73, "意见反馈 HTTP 请求超时"),
    ERR_PARAMETER_ABNORMAL(0x01F4, "Handy 上云退出参数异常"),
    DOWNCOMPLETE(0xFF74, "下载完成"),
    CALLBACK_TIMEOUT(0xFFFF, "回调超时");

    private static final Map<Integer, ViplexErrorCode> INDEX;

    static {
        Map<Integer, ViplexErrorCode> map = new HashMap<>();
        for (ViplexErrorCode e : values()) {
            map.put(e.code, e);
        }
        INDEX = Collections.unmodifiableMap(map);
    }

    private final int code;
    private final String description;

    /**
     * 按错误码查找枚举。
     *
     * @param code SDK 回调错误码
     * @return 匹配的枚举，未匹配返回 {@code null}
     */
    public static ViplexErrorCode of(int code) {
        return INDEX.get(code);
    }

    /**
     * 返回人可读的错误描述，格式：{@code ERR_UNSUPPORTED | 终端不支持}。
     *
     * @param code SDK 回调错误码
     * @return 描述字符串，未匹配时返回 {@code UNKNOWN_ERROR(code=38)}
     */
    public static String describe(int code) {
        ViplexErrorCode e = INDEX.get(code);
        if (e != null) {
            return String.format("code=%d (0x%X) | %s | %s", e.code, e.code, e.name(), e.description);
        }
        return String.format("UNKNOWN_ERROR(code=%d)", code);
    }
}
