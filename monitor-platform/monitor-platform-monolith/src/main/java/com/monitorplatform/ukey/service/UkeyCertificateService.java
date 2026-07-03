package com.monitorplatform.ukey.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitorplatform.ukey.entity.UkeyCertificate;
import com.monitorplatform.ukey.entity.dto.CertValidateRequestDTO;
import com.monitorplatform.ukey.entity.dto.CertValidateResponseDTO;

import java.util.List;

/**
 * UKey证书管理服务
 */
public interface UkeyCertificateService {

    /**
     * 校验UKey证书合法性
     * 依次检查：是否注册、状态、有效期、加密算法、绑定关系
     *
     * @param request 校验请求
     * @return 校验结果
     */
    CertValidateResponseDTO validateCertificate(CertValidateRequestDTO request);

    /**
     * 根据证书编号查询证书
     */
    UkeyCertificate getByCertSerialNo(String certSerialNo);

    /**
     * 根据 authId 前缀模糊匹配证书（cert_serial_no LIKE 'authId%'）
     * 用于 ParseAuthReq 阶段预加载客户端证书到缓存
     */
    UkeyCertificate getByCertSerialNoPrefix(String authIdPrefix);

    /**
     * 分页查询 UKey 证书列表
     *
     * @param keyword  关键词，模精匹配 Key名称（display_name）或序列号（cert_serial_no），为空则不过滤
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数
     * @return 分页结果
     */
    Page<UkeyCertificate> pageQuery(String keyword, int page, int pageSize);

    /**
     * 注册新证书
     */
    UkeyCertificate registerCertificate(UkeyCertificate certificate);

    /**
     * 更新证书整体信息（用于绑定 clientId 等字段更新）
     */
    void updateCertificate(UkeyCertificate certificate);

    /**
     * 更新证书状态
     */
    boolean updateCertStatus(String certSerialNo, String newStatus);

    /**
     * 更新证书备注
     */
    boolean updateRemark(String certSerialNo, String remark);

    /**
     * 物理删除 UKey 证书
     *
     * @param certSerialNo 证书编号
     * @return 是否删除成功
     */
    boolean deleteByCertSerialNo(String certSerialNo);

    /**
     * 绑定证书到客户端
     */
    boolean bindToClient(String certSerialNo, String clientId);

    /**
     * 更新客户端在线状态
     * 客户端认证成功时传入 ONLINE，UKey 拔出或主动退出时传入 OFFLINE
     *
     * @param certSerialNo 证书编号
     * @param clientId     客户端ID
     * @param onlineStatus ONLINE 或 OFFLINE
     * @return 是否更新成功
     */
    boolean updateOnlineStatus(String certSerialNo, String clientId, String onlineStatus);

    /**
     * 查询当前在线的 UKey 信息（登录页轮询接口）
     * 返回第一个 ONLINE 状态的证书信息，如果没有则返回 null
     */
    UkeyCertificate getOnlineUkey();

    /**
     * UKey 登录校验：校验证书编号 + PIN 码
     * 前置条件：该证书必须处于 ONLINE 状态（双向认证已成功）
     *
     * @param certSerialNo UKey 的 cerId（登录用户名）
     * @param pin          UKey PIN 码（登录密码）
     * @return 认证通过的证书对象，失败返回 null
     */
    UkeyCertificate loginByUkey(String certSerialNo, String pin);

    /**
     * 管理员手动导入 UKey 证书（直接生效 NORMAL）
     *
     * @param certSerialNo  证书编号（从 .cer 文件解析）
     * @param displayName   UKey 显示名称（管理员手填）
     * @param pin           UKey PIN 码（明文，存入时哈希化）
     * @param issuer        证书颁发机构（从 .cer 文件解析）
     * @param validFrom     证书生效时间（从 .cer 文件解析）
     * @param validUntil    证书失效时间（管理员选择）
     * @param remark        备注
     * @return 创建的证书对象
     */
    UkeyCertificate importCertificate(String certSerialNo, String displayName, String pin,
                                      String issuer, java.time.LocalDateTime validFrom,
                                      java.time.LocalDateTime validUntil, String remark);

    /**
     * 查询待审核的 UKey 证书列表
     */
    List<UkeyCertificate> listPendingCertificates();

    /**
     * 审核通过：PENDING → NORMAL
     *
     * @param certSerialNo 证书编号
     * @param pin          审核时可修改的 PIN 码（当建导入时未填 PIN，审核时补充）
     * @return 是否成功
     */
    boolean approveCertificate(String certSerialNo, String pin);

    /**
     * 审核拒绝：PENDING → REVOKED
     *
     * @param certSerialNo 证书编号
     * @return 是否成功
     */
    boolean rejectCertificate(String certSerialNo);

    /**
     * 将数据库中所有 onlineStatus=ONLINE 的 UKey 批量设为 OFFLINE
     * 在客户端后端服务掉线时由健康探活组件调用，保证状态数据真实性
     *
     * @return 影响的行数
     */
    int setAllOnlineUkeysOffline();

    /**
     * 更新客户端心跳时间与 IP
     * 客户端每隔 5 秒调用一次，管控平台以此判断客户端进程是否存活
     *
     * @param certSerialNo 证书编号（标识是哪个客户端）
     * @param clientId     客户端 ID
     * @param clientIp     客户端 IP 地址
     * @return 是否更新成功
     */
    boolean updateHeartbeat(String certSerialNo, String clientId, String clientIp);

    /**
     * 修改 PIN 码
     * 实现安全规范要求：密码定期更换、历史密码限制
     *
     * @param certSerialNo 证书编号
     * @param oldPin 旧密码（验证身份）
     * @param newPin 新密码
     * @return 是否修改成功
     */
    boolean changePassword(String certSerialNo, String oldPin, String newPin);
}