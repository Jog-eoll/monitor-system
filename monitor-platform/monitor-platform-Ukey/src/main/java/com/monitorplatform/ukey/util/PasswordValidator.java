package com.monitorplatform.ukey.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 密码复杂度校验工具类
 * 
 * 安全规范要求：
 * - 至少8位
 * - 包含字母 + 数字 + 特殊字符
 * - 无弱口令（如123456、admin123等）
 * - 用户名不允许使用默认账号（如root、admin、guest等）
 */
public class PasswordValidator {

    /** 最小密码长度 */
    private static final int MIN_LENGTH = 8;

    /** 包含字母的正则 */
    private static final Pattern LETTER_PATTERN = Pattern.compile(".*[a-zA-Z].*");

    /** 包含数字的正则 */
    private static final Pattern DIGIT_PATTERN = Pattern.compile(".*[0-9].*");

    /** 包含特殊字符的正则 */
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?~`].*");

    /** 禁止使用的默认账号名（不区分大小写） */
    private static final Set<String> FORBIDDEN_USERNAMES = new HashSet<>(Arrays.asList(
            "root", "admin", "guest", "test", "user", "administrator",
            "superuser", "super", "master", "manager", "operator",
            "default", "anonymous", "nobody", "sys", "system"
    ));

    /** 常见弱口令黑名单 */
    private static final Set<String> WEAK_PASSWORDS = new HashSet<>(Arrays.asList(
            // 纯数字序列
            "123456", "12345678", "123456789", "1234567890", "111111", "000000",
            "666666", "888888", "520520", "1314520",
            // 常见组合
            "password", "admin123", "admin1234", "admin888", "admin666",
            "root123", "root1234", "test123", "test1234",
            "qwerty", "qwerty123", "abc123", "abcd1234",
            // 键盘模式
            "1q2w3e", "1q2w3e4r", "qazwsx", "zaq12wsx",
            // 常见英文
            "letmein", "welcome", "welcome123", "login", "login123",
            "iloveyou", "sunshine", "princess", "monkey",
            // 中文拼音
            "woaini", "woaini1314", "zhangsan", "lisi", "wangwu",
            // 年份日期
            "20242024", "20252025", "20262026", "19901990", "20002000"
    ));

    /**
     * 校验用户名是否为禁用的默认账号名
     * 安全规范：禁止使用 root、admin、guest 等默认账号名
     *
     * @param username 用户名
     * @return 校验结果
     */
    public static ValidationResult validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return new ValidationResult(false, "用户名不能为空");
        }
        if (FORBIDDEN_USERNAMES.contains(username.trim().toLowerCase())) {
            return new ValidationResult(false,
                    "用户名 '" + username + "' 为系统禁用的默认账号名（如root、admin、guest等），请修改为其他用户名");
        }
        return new ValidationResult(true, "用户名合法");
    }

    /**
     * 校验密码强度
     * @param password 明文密码
     * @return 校验结果
     */
    public static ValidationResult validate(String password) {
        if (password == null || password.isEmpty()) {
            return new ValidationResult(false, "密码不能为空");
        }

        // 1. 长度检查
        if (password.length() < MIN_LENGTH) {
            return new ValidationResult(false, 
                    String.format("密码长度不足，至少需要%d位，当前%d位", MIN_LENGTH, password.length()));
        }

        // 2. 弱口令检查
        String lowerPassword = password.toLowerCase();
        if (WEAK_PASSWORDS.contains(lowerPassword)) {
            return new ValidationResult(false, "密码为常见弱口令，请设置更复杂的密码");
        }

        // 3. 包含字母检查
        if (!LETTER_PATTERN.matcher(password).matches()) {
            return new ValidationResult(false, "密码必须包含字母");
        }

        // 4. 包含数字检查
        if (!DIGIT_PATTERN.matcher(password).matches()) {
            return new ValidationResult(false, "密码必须包含数字");
        }

        // 5. 包含特殊字符检查
        if (!SPECIAL_CHAR_PATTERN.matcher(password).matches()) {
            return new ValidationResult(false, "密码必须包含特殊字符（如 !@#$%^&* 等）");
        }

        return new ValidationResult(true, "密码强度符合要求");
    }

    /**
     * 快速校验（仅返回布尔值）
     */
    public static boolean isValid(String password) {
        return validate(password).isValid();
    }

    /**
     * 校验结果DTO
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String message;

        public ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return valid ? "[通过] " + message : "[未通过] " + message;
        }
    }
}
