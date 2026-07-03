package com.monitorplatform.ukey.service;

/**
 * 登录安全服务接口
 * 实现登录失败锁定机制（安全规范第6条）
 * - 连续5次失败锁定10分钟
 * - 锁定时间到期后自动解锁
 */
public interface LoginSecurityService {

    /**
     * 检查指定证书是否被锁定
     * @param certSerialNo 证书序列号
     * @return true=已锁定，false=未锁定
     */
    boolean isLocked(String certSerialNo);

    /**
     * 获取剩余锁定时间（秒）
     * @param certSerialNo 证书序列号
     * @return 剩余秒数，0表示未锁定
     */
    long getLockRemainingSeconds(String certSerialNo);

    /**
     * 获取登录失败次数
     * @param certSerialNo 证书序列号
     * @return 失败次数
     */
    int getFailCount(String certSerialNo);

    /**
     * 记录登录失败
     * 失败次数+1，达到阈值则自动锁定指定时间
     * @param certSerialNo 证书序列号
     * @return 当前失败次数
     */
    int recordFail(String certSerialNo);

    /**
     * 清除登录失败记录（登录成功时调用）
     * @param certSerialNo 证书序列号
     */
    void clearFailCount(String certSerialNo);

    /**
     * 获取锁定状态描述
     * @param certSerialNo 证书序列号
     * @return 状态描述
     */
    LockStatus getLockStatus(String certSerialNo);

    /**
     * 锁定状态DTO
     */
    class LockStatus {
        private boolean locked;
        private int failCount;
        private long remainingSeconds;
        private String message;

        public LockStatus(boolean locked, int failCount, long remainingSeconds, String message) {
            this.locked = locked;
            this.failCount = failCount;
            this.remainingSeconds = remainingSeconds;
            this.message = message;
        }

        public boolean isLocked() { return locked; }
        public int getFailCount() { return failCount; }
        public long getRemainingSeconds() { return remainingSeconds; }
        public String getMessage() { return message; }
    }
}
