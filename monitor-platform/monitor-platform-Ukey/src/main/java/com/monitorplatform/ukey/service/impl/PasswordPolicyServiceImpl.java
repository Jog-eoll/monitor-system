package com.monitorplatform.ukey.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.monitorplatform.ukey.entity.PinHistory;
import com.monitorplatform.ukey.mapper.PinHistoryMapper;
import com.monitorplatform.ukey.service.PasswordPolicyService;
import com.monitorplatform.ukey.util.PasswordValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 密码策略服务实现
 */
@Slf4j
@Service
public class PasswordPolicyServiceImpl implements PasswordPolicyService {

    @Resource
    private PinHistoryMapper pinHistoryMapper;

    /** 密码有效期（天），安全规范要求≤90天 */
    @Value("${password.policy.valid-days:90}")
    private int passwordValidDays;

    /** 历史密码保留数量 */
    @Value("${password.policy.history-keep-count:5}")
    private int historyKeepCount;

    @Override
    public PasswordValidator.ValidationResult validatePasswordStrength(String password) {
        return PasswordValidator.validate(password);
    }

    @Override
    public boolean isPasswordExpired(LocalDateTime pinUpdateTime) {
        if (pinUpdateTime == null) {
            // 从未设置过密码，视为需要设置
            return true;
        }
        long daysSinceUpdate = ChronoUnit.DAYS.between(pinUpdateTime, LocalDateTime.now());
        return daysSinceUpdate > passwordValidDays;
    }

    @Override
    public int getRemainingValidDays(LocalDateTime pinUpdateTime) {
        if (pinUpdateTime == null) {
            return 0;
        }
        long daysSinceUpdate = ChronoUnit.DAYS.between(pinUpdateTime, LocalDateTime.now());
        return (int) (passwordValidDays - daysSinceUpdate);
    }

    @Override
    public boolean isPasswordInHistory(String certSerialNo, String password) {
        List<PinHistory> histories = pinHistoryMapper.findRecentByCertSerialNo(certSerialNo, historyKeepCount);
        
        for (PinHistory history : histories) {
            // 使用相同的盐值计算哈希
            String inputHash = sha256WithSalt(password, history.getPinSalt());
            if (inputHash.equals(history.getPinHash())) {
                log.warn("[密码策略] 密码在历史记录中找到: certSerialNo={}", certSerialNo);
                return true;
            }
        }
        return false;
    }

    @Override
    public void savePasswordHistory(String certSerialNo, String pinHash, String pinSalt) {
        PinHistory history = new PinHistory();
        history.setCertSerialNo(certSerialNo);
        history.setPinHash(pinHash);
        history.setPinSalt(pinSalt);
        history.setCreateTime(LocalDateTime.now());
        pinHistoryMapper.insert(history);
        
        // 清理超出保留数量的旧记录
        cleanOldHistories(certSerialNo);
        
        log.info("[密码策略] 保存历史密码: certSerialNo={}", certSerialNo);
    }

    @Override
    public int getPasswordValidDays() {
        return passwordValidDays;
    }

    @Override
    public int getHistoryKeepCount() {
        return historyKeepCount;
    }

    /**
     * 清理超出保留数量的历史记录
     */
    private void cleanOldHistories(String certSerialNo) {
        LambdaQueryWrapper<PinHistory> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(PinHistory::getCertSerialNo, certSerialNo);
        Long count = pinHistoryMapper.selectCount(countWrapper);
        
        if (count > historyKeepCount) {
            // 删除最旧的记录，保留最近N条
            LambdaQueryWrapper<PinHistory> deleteWrapper = new LambdaQueryWrapper<>();
            deleteWrapper.eq(PinHistory::getCertSerialNo, certSerialNo)
                    .orderByDesc(PinHistory::getCreateTime)
            ;
            // 查询要保留的记录ID
            List<PinHistory> toKeep = pinHistoryMapper.selectList(deleteWrapper);
            if (toKeep.size() > historyKeepCount) {
                // 删除超出的记录
                LambdaQueryWrapper<PinHistory> deleteOld = new LambdaQueryWrapper<>();
                deleteOld.eq(PinHistory::getCertSerialNo, certSerialNo)
                        .notIn(PinHistory::getId, 
                                toKeep.subList(0, Math.min(historyKeepCount, toKeep.size()))
                                        .stream().map(PinHistory::getId).collect(Collectors.toList()));
                pinHistoryMapper.delete(deleteOld);
            }
        }
    }

    /**
     * SHA-256 加盐哈希（与 UkeyCertificateServiceImpl 中保持一致）
     */
    private String sha256WithSalt(String input, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
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
}
