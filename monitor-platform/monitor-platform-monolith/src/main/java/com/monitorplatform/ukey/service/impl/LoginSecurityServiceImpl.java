package com.monitorplatform.ukey.service.impl;

import com.monitorplatform.ukey.service.LoginSecurityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * 登录安全服务实现
 * 
 * 实现安全规范第6条要求：
 * - 连续5次失败锁定10分钟
 * - 锁定时间到期后自动解锁
 * 
 * Redis 存储结构：
 * - login:fail:{certSerialNo}  -> 失败次数，30分钟过期
 * - login:lock:{certSerialNo}  -> 锁定标记，设置过期时间自动解锁
 */
@Slf4j
@Service
public class LoginSecurityServiceImpl implements LoginSecurityService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 登录失败最大次数阈值 */
    @Value("${login.security.max-fail-count:5}")
    private int maxFailCount;

    /** 锁定时长（分钟），安全规范要求>=10分钟 */
    @Value("${login.security.lock-duration-minutes:10}")
    private int lockDurationMinutes;

    /** 失败计数过期时间（分钟） */
    @Value("${login.security.fail-count-expire-minutes:30}")
    private int failCountExpireMinutes;

    /** Redis Key 前缀 */
    private static final String FAIL_COUNT_PREFIX = "login:fail:";
    private static final String LOCK_PREFIX = "login:lock:";

    @Override
    public boolean isLocked(String certSerialNo) {
        String lockKey = LOCK_PREFIX + certSerialNo;
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey));
    }

    @Override
    public long getLockRemainingSeconds(String certSerialNo) {
        String lockKey = LOCK_PREFIX + certSerialNo;
        Long ttl = stringRedisTemplate.getExpire(lockKey, TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? ttl : 0;
    }

    @Override
    public int getFailCount(String certSerialNo) {
        String failKey = FAIL_COUNT_PREFIX + certSerialNo;
        String count = stringRedisTemplate.opsForValue().get(failKey);
        return count != null ? Integer.parseInt(count) : 0;
    }

    @Override
    public int recordFail(String certSerialNo) {
        String failKey = FAIL_COUNT_PREFIX + certSerialNo;
        
        // 增加失败计数
        Long count = stringRedisTemplate.opsForValue().increment(failKey);
        int failCount = count != null ? count.intValue() : 1;
        
        // 设置过期时间（首次失败时设置）
        if (failCount == 1) {
            stringRedisTemplate.expire(failKey, failCountExpireMinutes, TimeUnit.MINUTES);
        }
        
        log.warn("[登录安全] PIN码验证失败: certSerialNo={}, 失败次数={}/{}", 
                certSerialNo, failCount, maxFailCount);
        
        // 达到阈值，触发锁定
        if (failCount >= maxFailCount) {
            lockAccount(certSerialNo);
            log.error("[登录安全] 触发锁定: certSerialNo={}, 连续失败{}次，锁定{}分钟后自动解锁", 
                    certSerialNo, failCount, lockDurationMinutes);
        }
        
        return failCount;
    }

    @Override
    public void clearFailCount(String certSerialNo) {
        String failKey = FAIL_COUNT_PREFIX + certSerialNo;
        stringRedisTemplate.delete(failKey);
        log.info("[登录安全] 清除失败计数: certSerialNo={}", certSerialNo);
    }

    @Override
    public LockStatus getLockStatus(String certSerialNo) {
        boolean locked = isLocked(certSerialNo);
        int failCount = getFailCount(certSerialNo);
        long remainingSeconds = locked ? getLockRemainingSeconds(certSerialNo) : 0;
        
        String message;
        if (locked) {
            long remainingMinutes = (remainingSeconds + 59) / 60; // 向上取整
            message = String.format("账户已锁定，剩余%d分钟%d秒后自动解锁", 
                    remainingSeconds / 60, remainingSeconds % 60);
        } else if (failCount > 0) {
            message = String.format("已失败%d次，连续失败%d次将锁定账户", failCount, maxFailCount);
        } else {
            message = "正常";
        }
        
        return new LockStatus(locked, failCount, remainingSeconds, message);
    }

    /**
     * 锁定账户（内部方法）
     * 锁定后设置过期时间，到期自动解锁
     */
    private void lockAccount(String certSerialNo) {
        String lockKey = LOCK_PREFIX + certSerialNo;
        // 存储锁定标记，值为锁定时间戳，设置过期时间自动解锁
        stringRedisTemplate.opsForValue().set(lockKey, 
                String.valueOf(System.currentTimeMillis()),
                lockDurationMinutes, TimeUnit.MINUTES);
    }
}
