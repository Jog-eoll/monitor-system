package com.monitorplatform.ukey.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitorplatform.ukey.entity.UkeyCertificate;
import com.monitorplatform.ukey.entity.dto.CertValidateRequestDTO;
import com.monitorplatform.ukey.entity.dto.CertValidateResponseDTO;
import com.monitorplatform.ukey.mapper.UkeyCertificateMapper;
import com.monitorplatform.ukey.service.UkeyCertificateService;
import com.monitorplatform.ukey.service.PasswordPolicyService;
import com.monitorplatform.ukey.util.PasswordValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * UKey证书管理服务实现
 */
@Slf4j
@Service
public class UkeyCertificateServiceImpl implements UkeyCertificateService {

    @Resource
    private UkeyCertificateMapper ukeyCertificateMapper;

    @Resource
    private PasswordPolicyService passwordPolicyService;

    /** getOnlineUkey 本地缓存：避免前端高频轮询把连接池打满，缓存有效期 2 秒 */
    private static final long ONLINE_CACHE_TTL_MS = 2000L;
    private final AtomicReference<UkeyCertificate> onlineUkeyCache = new AtomicReference<>();
    private final AtomicLong onlineUkeyCacheTime = new AtomicLong(0L);

    /** 合规的加密算法列表 */
    private static final List<String> COMPLIANT_ALGORITHMS = Arrays.asList("SM2", "SM9");

    @Override
    public CertValidateResponseDTO validateCertificate(CertValidateRequestDTO request) {
        log.info("开始校验UKey证书: certSerialNo={}, clientId={}", request.getCertSerialNo(), request.getClientId());

        // 1. 查询证书是否存在
        UkeyCertificate cert = getByCertSerialNo(request.getCertSerialNo());
        if (cert == null) {
            log.warn("证书未注册: certSerialNo={}", request.getCertSerialNo());
            return CertValidateResponseDTO.fail(1, "证书未注册，请先在平台导入该UKey");
        }

        // 2. 校验证书状态
        if ("PENDING".equals(cert.getCertStatus())) {
            log.warn("证书尚未生效: certSerialNo={}", request.getCertSerialNo());
            return CertValidateResponseDTO.fail(8, "证书尚未生效，请联系管理员处理");
        }
        if ("REVOKED".equals(cert.getCertStatus())) {
            log.warn("证书已注销: certSerialNo={}", request.getCertSerialNo());
            return CertValidateResponseDTO.fail(2, "证书已注销");
        }
        if ("LOST".equals(cert.getCertStatus())) {
            log.warn("证书已挂失: certSerialNo={}", request.getCertSerialNo());
            return CertValidateResponseDTO.fail(3, "证书已挂失");
        }
        if (!"NORMAL".equals(cert.getCertStatus())) {
            log.warn("证书状态异常: certSerialNo={}, status={}", request.getCertSerialNo(), cert.getCertStatus());
            return CertValidateResponseDTO.fail(2, "证书状态异常: " + cert.getCertStatus());
        }

        // 3. 校验有效期
        LocalDateTime now = LocalDateTime.now();
        if (cert.getValidUntil() != null && now.isAfter(cert.getValidUntil())) {
            log.warn("证书已过期: certSerialNo={}, validUntil={}", request.getCertSerialNo(), cert.getValidUntil());
            return CertValidateResponseDTO.fail(4, "证书已过期");
        }
        if (cert.getValidFrom() != null && now.isBefore(cert.getValidFrom())) {
            log.warn("证书未生效: certSerialNo={}, validFrom={}", request.getCertSerialNo(), cert.getValidFrom());
            return CertValidateResponseDTO.fail(5, "证书未生效");
        }

        // 4. 校验加密算法是否合规（国密标准）
        if (cert.getCryptoAlgorithm() != null && !COMPLIANT_ALGORITHMS.contains(cert.getCryptoAlgorithm())) {
            log.warn("加密算法不合规: certSerialNo={}, algorithm={}", request.getCertSerialNo(), cert.getCryptoAlgorithm());
            return CertValidateResponseDTO.fail(6, "加密算法不符合国密标准: " + cert.getCryptoAlgorithm());
        }

        // 5. 校验绑定关系
        if (cert.getBoundClientId() != null && !cert.getBoundClientId().isEmpty()) {
            if (!cert.getBoundClientId().equals(request.getClientId())) {
                log.warn("证书与客户端不匹配: certSerialNo={}, boundClientId={}, requestClientId={}",
                        request.getCertSerialNo(), cert.getBoundClientId(), request.getClientId());
                return CertValidateResponseDTO.fail(7, "证书与当前客户端不匹配");
            }
        }

        log.info("证书校验通过: certSerialNo={}, clientId={}", request.getCertSerialNo(), request.getClientId());
        return CertValidateResponseDTO.success(cert.getCertSerialNo(), cert.getBoundClientId());
    }

    @Override
    public UkeyCertificate getByCertSerialNo(String certSerialNo) {
        LambdaQueryWrapper<UkeyCertificate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UkeyCertificate::getCertSerialNo, certSerialNo);
        return ukeyCertificateMapper.selectOne(wrapper);
    }

    @Override
    public UkeyCertificate getByCertSerialNoPrefix(String authIdPrefix) {
        // 先精确匹配
        UkeyCertificate cert = getByCertSerialNo(authIdPrefix);
        if (cert != null) {
            return cert;
        }
        // 再前缀匹配：authId = "44030000003330000305"，库中存的可能是 "44030000003330000305_IE2D11"
        LambdaQueryWrapper<UkeyCertificate> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(UkeyCertificate::getCertSerialNo, authIdPrefix)
               .last("LIMIT 1");
        return ukeyCertificateMapper.selectOne(wrapper);
    }

    @Override
    public Page<UkeyCertificate> pageQuery(String keyword, int page, int pageSize) {
        LambdaQueryWrapper<UkeyCertificate> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            // 关键词同时匹配 Key名称或序列号
            wrapper.and(w -> w
                .like(UkeyCertificate::getDisplayName, keyword.trim())
                .or()
                .like(UkeyCertificate::getCertSerialNo, keyword.trim())
            );
        }
        // 按建入时间正序排列（新导入的在后面）——改为倒序可改 orderByDesc
        wrapper.orderByDesc(UkeyCertificate::getCreateTime);
        Page<UkeyCertificate> pageObj = new Page<>(page, pageSize);
        return ukeyCertificateMapper.selectPage(pageObj, wrapper);
    }

    @Override
    public UkeyCertificate registerCertificate(UkeyCertificate certificate) {
        certificate.setCertStatus("NORMAL");
        certificate.setCreateTime(LocalDateTime.now());
        certificate.setUpdateTime(LocalDateTime.now());
        ukeyCertificateMapper.insert(certificate);
        return certificate;
    }

    @Override
    public void updateCertificate(UkeyCertificate certificate) {
        certificate.setUpdateTime(LocalDateTime.now());
        ukeyCertificateMapper.updateById(certificate);
    }

    @Override
    public boolean updateCertStatus(String certSerialNo, String newStatus) {
        LambdaUpdateWrapper<UkeyCertificate> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UkeyCertificate::getCertSerialNo, certSerialNo)
                .set(UkeyCertificate::getCertStatus, newStatus)
                .set(UkeyCertificate::getUpdateTime, LocalDateTime.now());
        return ukeyCertificateMapper.update(null, wrapper) > 0;
    }

    @Override
    public boolean updateRemark(String certSerialNo, String remark) {
        LambdaUpdateWrapper<UkeyCertificate> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UkeyCertificate::getCertSerialNo, certSerialNo)
                .set(UkeyCertificate::getRemark, remark)
                .set(UkeyCertificate::getUpdateTime, LocalDateTime.now());
        return ukeyCertificateMapper.update(null, wrapper) > 0;
    }

    @Override
    public boolean deleteByCertSerialNo(String certSerialNo) {
        LambdaQueryWrapper<UkeyCertificate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UkeyCertificate::getCertSerialNo, certSerialNo);
        int rows = ukeyCertificateMapper.delete(wrapper);
        if (rows > 0) {
            log.info("[UKey删除] 物理删除成功: certSerialNo={}", certSerialNo);
        } else {
            log.warn("[UKey删除] 证书不存在: certSerialNo={}", certSerialNo);
        }
        return rows > 0;
    }

    @Override
    public boolean bindToClient(String certSerialNo, String clientId) {
        LambdaUpdateWrapper<UkeyCertificate> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UkeyCertificate::getCertSerialNo, certSerialNo)
                .set(UkeyCertificate::getBoundClientId, clientId)
                .set(UkeyCertificate::getUpdateTime, LocalDateTime.now());
        return ukeyCertificateMapper.update(null, wrapper) > 0;
    }

    @Override
    public boolean updateOnlineStatus(String certSerialNo, String clientId, String onlineStatus) {
        log.info("更新客户端在线状态: certSerialNo={}, clientId={}, onlineStatus={}",
                certSerialNo, clientId, onlineStatus);
        LambdaUpdateWrapper<UkeyCertificate> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UkeyCertificate::getCertSerialNo, certSerialNo);
        wrapper.set(UkeyCertificate::getOnlineStatus, onlineStatus);
        wrapper.set(UkeyCertificate::getUpdateTime, LocalDateTime.now());
        if ("ONLINE".equals(onlineStatus)) {
            // 认证成功：同时绑定客户端、更新最近认证时间、初始化心跳时间
            wrapper.set(UkeyCertificate::getBoundClientId, clientId);
            wrapper.set(UkeyCertificate::getLastAuthTime, LocalDateTime.now());
            wrapper.set(UkeyCertificate::getLastHeartbeatTime, LocalDateTime.now());
        } else {
            // 离线：更新最近离线时间
            wrapper.set(UkeyCertificate::getLastOfflineTime, LocalDateTime.now());
        }
        boolean updated = ukeyCertificateMapper.update(null, wrapper) > 0;
        if (updated) {
            // 清除本地缓存，确保后续 getOnlineUkey() 拿到最新数据
            onlineUkeyCache.set(null);
            onlineUkeyCacheTime.set(0L);
        }
        return updated;
    }

    @Override
    public UkeyCertificate getOnlineUkey() {
        long now = System.currentTimeMillis();
        // 缓存命中：2秒内直接返回缓存，不查库，防止高频轮询打满连接池
        if (now - onlineUkeyCacheTime.get() < ONLINE_CACHE_TTL_MS) {
            return onlineUkeyCache.get();
        }
        LambdaQueryWrapper<UkeyCertificate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UkeyCertificate::getOnlineStatus, "ONLINE")
               .last("LIMIT 1");
        UkeyCertificate result = ukeyCertificateMapper.selectOne(wrapper);
        onlineUkeyCache.set(result);
        onlineUkeyCacheTime.set(now);
        return result;
    }

    @Override
    public UkeyCertificate loginByUkey(String certSerialNo, String pin) {
        // 1. 查询证书：先精确匹配，失败则按前缀模精匹配
        UkeyCertificate cert = getByCertSerialNo(certSerialNo);
        if (cert == null) {
            // 尝试前缀匹配：输入的是短前缀（如 44030000003330000126），实际库中存的是含后缀的完整值
            LambdaQueryWrapper<UkeyCertificate> likeWrapper = new LambdaQueryWrapper<>();
            likeWrapper.likeRight(UkeyCertificate::getCertSerialNo, certSerialNo)
                       .last("LIMIT 1");
            cert = ukeyCertificateMapper.selectOne(likeWrapper);
            if (cert != null) {
                log.info("[UKey登录] 前缀匹配成功: 输入={}, 实际={}", certSerialNo, cert.getCertSerialNo());
            }
        }
        if (cert == null) {
            log.warn("[UKey登录] 证书不存在: certSerialNo={}", certSerialNo);
            return null;
        }
        // 2. 必须处于 ONLINE 状态（双向认证已完成）
        if (!"ONLINE".equals(cert.getOnlineStatus())) {
            log.warn("[UKey登录] UKey 未在线或未认证: certSerialNo={}, status={}",
                    cert.getCertSerialNo(), cert.getOnlineStatus());
            return null;
        }
        // 3. 证书本身必须有效
        if (!"NORMAL".equals(cert.getCertStatus())) {
            log.warn("[UKey登录] 证书状态异常: certSerialNo={}, certStatus={}",
                    cert.getCertSerialNo(), cert.getCertStatus());
            return null;
        }
        
        // 4. 检查密码是否过期（安全规范要求）
        if (passwordPolicyService.isPasswordExpired(cert.getPinUpdateTime())) {
            int remainingDays = passwordPolicyService.getRemainingValidDays(cert.getPinUpdateTime());
            log.warn("[UKey登录] PIN码已过期，请修改密码: certSerialNo={}, 已过期{}天",
                    cert.getCertSerialNo(), Math.abs(remainingDays));
            throw new IllegalStateException("PIN码已超过" + passwordPolicyService.getPasswordValidDays() + 
                    "天有效期，请修改密码后重新登录");
        }
        
        // 5. 校验 PIN 码（支持新旧两种格式，向后兼容）
        if (cert.getPinHash() == null || cert.getPinHash().isEmpty()) {
            log.warn("[UKey登录] 证书未配置 PIN 码: certSerialNo={}", cert.getCertSerialNo());
            return null;
        }
        
        // 安全校验：使用加盐 SHA-256 验证 PIN 码
        // 新格式：pinSalt 不为空，使用 salt+pin 计算
        // 旧格式兼容：pinSalt 为空，使用纯 SHA-256（待旧数据迁移后可移除此兼容逻辑）
        String inputHash;
        if (cert.getPinSalt() != null && !cert.getPinSalt().isEmpty()) {
            // 新格式：加盐哈希
            inputHash = sha256WithSalt(pin, cert.getPinSalt());
        } else {
            // 旧格式兼容：无盐哈希（向后兼容，建议迁移后移除）
            inputHash = sha256(pin);
        }
        
        if (!cert.getPinHash().equals(inputHash)) {
            log.warn("[UKey登录] PIN 码错误: certSerialNo={}", cert.getCertSerialNo());
            return null;
        }
        log.info("[UKey登录] 登录成功: certSerialNo={}", cert.getCertSerialNo());
        return cert;
    }

    /** 生成随机盐值（32字节 = 64位十六进制字符串） */
    private String generateSalt() {
        byte[] salt = new byte[32];
        new java.security.SecureRandom().nextBytes(salt);
        StringBuilder sb = new StringBuilder();
        for (byte b : salt) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /** SHA-256 加盐哈希（安全规范要求） */
    private String sha256WithSalt(String input, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // 先加盐，再哈希，防止彩虹表攻击
            String saltedInput = salt + input;
            byte[] hash = digest.digest(saltedInput.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256加盐哈希失败", e);
        }
    }

    /** SHA-256 哈希（旧格式兼容，待迁移完成后可移除） */
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256哈希失败", e);
        }
    }

    @Override
    public UkeyCertificate importCertificate(String certSerialNo, String displayName, String pin,
                                              String issuer, java.time.LocalDateTime validFrom,
                                              java.time.LocalDateTime validUntil, String remark) {
        // 检查是否已存在：已存在则覆盖更新，不报错
        UkeyCertificate existing = getByCertSerialNo(certSerialNo);
        if (existing != null) {
            log.info("[UKey导入] 证书已存在，执行覆盖更新: certSerialNo={}", certSerialNo);
            existing.setDisplayName(displayName != null && !displayName.isEmpty() ? displayName : certSerialNo);
            existing.setIssuer(issuer);
            if (validFrom != null) existing.setValidFrom(validFrom);
            if (validUntil != null) existing.setValidUntil(validUntil);
            existing.setCryptoAlgorithm("SM2");
            if (remark != null) existing.setRemark(remark);
            if (pin != null && !pin.isEmpty()) {
                // 密码复杂度校验
                PasswordValidator.ValidationResult validationResult = passwordPolicyService.validatePasswordStrength(pin);
                if (!validationResult.isValid()) {
                    throw new IllegalArgumentException("PIN码不符合安全要求: " + validationResult.getMessage());
                }
                        
                // 历史密码检查
                if (passwordPolicyService.isPasswordInHistory(certSerialNo, pin)) {
                    throw new IllegalArgumentException("PIN码不能与最近" + passwordPolicyService.getHistoryKeepCount() + "次使用的密码相同");
                }
                        
                // 使用加盐哈希存储 PIN 码（安全规范要求）
                String salt = generateSalt();
                String pinHash = sha256WithSalt(pin, salt);
                existing.setPinSalt(salt);
                existing.setPinHash(pinHash);
                existing.setPinUpdateTime(LocalDateTime.now());
                        
                // 保存到历史密码记录
                passwordPolicyService.savePasswordHistory(certSerialNo, pinHash, salt);
            }
            // 覆盖时保持状态为 NORMAL，确保可用
            existing.setCertStatus("NORMAL");
            existing.setUpdateTime(LocalDateTime.now());
            ukeyCertificateMapper.updateById(existing);
            log.info("[UKey导入] 覆盖更新成功: certSerialNo={}", certSerialNo);
            return existing;
        }
        UkeyCertificate cert = new UkeyCertificate();
        cert.setCertSerialNo(certSerialNo);
        // displayName 优先用传入的，若为空则默认用 certSerialNo
        cert.setDisplayName(displayName != null && !displayName.isEmpty() ? displayName : certSerialNo);
        // 管理员主动导入，直接生效，无需审核
        cert.setCertStatus("NORMAL");
        cert.setOnlineStatus("OFFLINE");
        // 使用从 .cer 文件解析的属性
        cert.setIssuer(issuer);
        cert.setValidFrom(validFrom != null ? validFrom : LocalDateTime.now());
        cert.setValidUntil(validUntil);
        cert.setCryptoAlgorithm("SM2");
        cert.setRemark(remark);
        // PIN 不为空则使用加盐哈希存储（安全规范要求）
        if (pin != null && !pin.isEmpty()) {
            // 密码复杂度校验
            PasswordValidator.ValidationResult validationResult = passwordPolicyService.validatePasswordStrength(pin);
            if (!validationResult.isValid()) {
                throw new IllegalArgumentException("PIN码不符合安全要求: " + validationResult.getMessage());
            }
            
            String salt = generateSalt();
            String pinHash = sha256WithSalt(pin, salt);
            cert.setPinSalt(salt);
            cert.setPinHash(pinHash);
            cert.setPinUpdateTime(LocalDateTime.now());
            
            // 保存到历史密码记录
            passwordPolicyService.savePasswordHistory(certSerialNo, pinHash, salt);
        }
        cert.setCreateTime(LocalDateTime.now());
        cert.setUpdateTime(LocalDateTime.now());
        ukeyCertificateMapper.insert(cert);
        log.info("[UKey导入] 管理员导入成功，直接生效: certSerialNo={}, displayName={}, issuer={}, validFrom={}",
                certSerialNo, cert.getDisplayName(), issuer, cert.getValidFrom());
        return cert;
    }

    @Override
    public List<UkeyCertificate> listPendingCertificates() {
        LambdaQueryWrapper<UkeyCertificate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UkeyCertificate::getCertStatus, "PENDING")
               .orderByDesc(UkeyCertificate::getCreateTime);
        return ukeyCertificateMapper.selectList(wrapper);
    }

    @Override
    public boolean approveCertificate(String certSerialNo, String pin) {
        UkeyCertificate cert = getByCertSerialNo(certSerialNo);
        if (cert == null) {
            log.warn("[审核通过] 证书不存在: certSerialNo={}", certSerialNo);
            return false;
        }
        if (!"PENDING".equals(cert.getCertStatus())) {
            log.warn("[审核通过] 证书不在待审核状态: certSerialNo={}, status={}", certSerialNo, cert.getCertStatus());
            return false;
        }
        LambdaUpdateWrapper<UkeyCertificate> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UkeyCertificate::getCertSerialNo, certSerialNo)
               .set(UkeyCertificate::getCertStatus, "NORMAL")
               .set(UkeyCertificate::getUpdateTime, LocalDateTime.now());
        // 审核时若传入了新 PIN，使用加盐哈希存储
        if (pin != null && !pin.isEmpty()) {
            // 密码复杂度校验
            PasswordValidator.ValidationResult validationResult = passwordPolicyService.validatePasswordStrength(pin);
            if (!validationResult.isValid()) {
                throw new IllegalArgumentException("PIN码不符合安全要求: " + validationResult.getMessage());
            }
            
            // 历史密码检查
            if (passwordPolicyService.isPasswordInHistory(certSerialNo, pin)) {
                throw new IllegalArgumentException("PIN码不能与最近" + passwordPolicyService.getHistoryKeepCount() + "次使用的密码相同");
            }
            
            String salt = generateSalt();
            String pinHash = sha256WithSalt(pin, salt);
            wrapper.set(UkeyCertificate::getPinSalt, salt);
            wrapper.set(UkeyCertificate::getPinHash, pinHash);
            wrapper.set(UkeyCertificate::getPinUpdateTime, LocalDateTime.now());
            
            // 保存到历史密码记录
            passwordPolicyService.savePasswordHistory(certSerialNo, pinHash, salt);
        }
        boolean result = ukeyCertificateMapper.update(null, wrapper) > 0;
        if (result) {
            log.info("[审核通过] 证书审核通过: certSerialNo={}", certSerialNo);
        }
        return result;
    }

    @Override
    public boolean rejectCertificate(String certSerialNo) {
        LambdaUpdateWrapper<UkeyCertificate> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UkeyCertificate::getCertSerialNo, certSerialNo)
               .eq(UkeyCertificate::getCertStatus, "PENDING")
               .set(UkeyCertificate::getCertStatus, "REVOKED")
               .set(UkeyCertificate::getUpdateTime, LocalDateTime.now());
        boolean result = ukeyCertificateMapper.update(null, wrapper) > 0;
        if (result) {
            log.info("[审核拒绝] 证书已拒绝: certSerialNo={}", certSerialNo);
        }
        return result;
    }

    @Override
    public int setAllOnlineUkeysOffline() {
        LambdaUpdateWrapper<UkeyCertificate> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UkeyCertificate::getOnlineStatus, "ONLINE")
               .set(UkeyCertificate::getOnlineStatus, "OFFLINE")
               .set(UkeyCertificate::getLastOfflineTime, LocalDateTime.now())
               .set(UkeyCertificate::getUpdateTime, LocalDateTime.now());
        int rows = ukeyCertificateMapper.update(null, wrapper);
        // 同步清除本地缓存，确保下次 getOnlineUkey() 从数据库重新查询
        onlineUkeyCache.set(null);
        onlineUkeyCacheTime.set(0L);
        log.info("[ClientOffline] 批量 OFFLINE 完成，影响行数={}", rows);
        return rows;
    }

    @Override
    public boolean updateHeartbeat(String certSerialNo, String clientId, String clientIp) {
        LambdaUpdateWrapper<UkeyCertificate> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UkeyCertificate::getCertSerialNo, certSerialNo)
               .set(UkeyCertificate::getOnlineStatus, "ONLINE")
               .set(UkeyCertificate::getLastHeartbeatTime, LocalDateTime.now())
               .set(UkeyCertificate::getUpdateTime, LocalDateTime.now());
        if (clientId != null && !clientId.trim().isEmpty()) {
            wrapper.set(UkeyCertificate::getBoundClientId, clientId);
        }
        if (clientIp != null && !clientIp.isEmpty()) {
            wrapper.set(UkeyCertificate::getClientIp, clientIp);
        }
        int rows = ukeyCertificateMapper.update(null, wrapper);
        if (rows > 0) {
            onlineUkeyCache.set(null);
            onlineUkeyCacheTime.set(0L);
        }
        return rows > 0;
    }

    @Override
    public boolean changePassword(String certSerialNo, String oldPin, String newPin) {
        // 1. 查询证书
        UkeyCertificate cert = getByCertSerialNo(certSerialNo);
        if (cert == null) {
            log.warn("[修改密码] 证书不存在: certSerialNo={}", certSerialNo);
            return false;
        }
        
        // 2. 验证旧密码
        if (cert.getPinHash() == null || cert.getPinHash().isEmpty()) {
            log.warn("[修改密码] 证书未设置密码: certSerialNo={}", certSerialNo);
            return false;
        }
        
        String oldPinHash;
        if (cert.getPinSalt() != null && !cert.getPinSalt().isEmpty()) {
            oldPinHash = sha256WithSalt(oldPin, cert.getPinSalt());
        } else {
            oldPinHash = sha256(oldPin);
        }
        
        if (!cert.getPinHash().equals(oldPinHash)) {
            log.warn("[修改密码] 旧密码错误: certSerialNo={}", certSerialNo);
            return false;
        }
        
        // 3. 校验新密码复杂度
        PasswordValidator.ValidationResult validationResult = passwordPolicyService.validatePasswordStrength(newPin);
        if (!validationResult.isValid()) {
            throw new IllegalArgumentException("新密码不符合安全要求: " + validationResult.getMessage());
        }
        
        // 4. 检查历史密码
        if (passwordPolicyService.isPasswordInHistory(certSerialNo, newPin)) {
            throw new IllegalArgumentException("新密码不能与最近" + passwordPolicyService.getHistoryKeepCount() + "次使用的密码相同");
        }
        
        // 5. 更新密码
        String newSalt = generateSalt();
        String newPinHash = sha256WithSalt(newPin, newSalt);
        
        LambdaUpdateWrapper<UkeyCertificate> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UkeyCertificate::getCertSerialNo, certSerialNo)
               .set(UkeyCertificate::getPinHash, newPinHash)
               .set(UkeyCertificate::getPinSalt, newSalt)
               .set(UkeyCertificate::getPinUpdateTime, LocalDateTime.now())
               .set(UkeyCertificate::getUpdateTime, LocalDateTime.now());
        int rows = ukeyCertificateMapper.update(null, wrapper);
        
        if (rows > 0) {
            // 6. 保存到历史密码记录
            passwordPolicyService.savePasswordHistory(certSerialNo, newPinHash, newSalt);
            log.info("[修改密码] 密码修改成功: certSerialNo={}", certSerialNo);
            return true;
        }
        return false;
    }
}
