package com.monitorplatform.ukey.config;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简易 Token 内存存储
 * 登录成功后存入 Token，拦截器校验时查询，退出时清除
 */
@Component
public class TokenStore {

    /** key=token, value=certSerialNo（或 "ADMIN"） */
    private final ConcurrentHashMap<String, String> tokenMap = new ConcurrentHashMap<>();

    /**
     * 存入 Token
     *
     * @param token        登录生成的 Token
     * @param certSerialNo 对应的证书编号（管理员传 "ADMIN"）
     */
    public void put(String token, String certSerialNo) {
        tokenMap.put(token, certSerialNo);
    }

    /**
     * 校验 Token 是否有效
     */
    public boolean isValid(String token) {
        return token != null && tokenMap.containsKey(token);
    }

    /**
     * 获取 Token 对应的身份
     */
    public String getIdentity(String token) {
        return tokenMap.get(token);
    }

    /**
     * 清除 Token（退出登录时调用）
     */
    public void remove(String token) {
        tokenMap.remove(token);
    }

    /**
     * 清除所有 Token（服务端 UKey 拔出时调用，强制所有在线用户下线）
     *
     * @return 被清除的 Token 数量
     */
    public int removeAll() {
        int count = tokenMap.size();
        tokenMap.clear();
        return count;
    }

    /**
     * 获取所有 Token 列表（用于服务端 UKey 拔出时全量写入黑名单）
     *
     * @return 所有 Token 列表
     */
    public List<String> getAllTokens() {
        return new java.util.ArrayList<>(tokenMap.keySet());
    }

    /**
     * 按 certSerialNo 清除该证书对应的所有 Token
     * UKey 拔出时调用，强制该 UKey 登录的所有会话失效
     *
     * @param certSerialNo 证书编号
     * @return 被清除的 Token 数量
     */
    public int removeByIdentity(String certSerialNo) {
        int count = 0;
        for (java.util.Map.Entry<String, String> entry : tokenMap.entrySet()) {
            if (certSerialNo.equals(entry.getValue())) {
                tokenMap.remove(entry.getKey());
                count++;
            }
        }
        return count;
    }

    /**
     * 获取某个 certSerialNo 对应的所有 Token 列表
     * 在加入 Redis 黑名单时使用，调用此方法前不先清除
     *
     * @param certSerialNo 证书编号
     * @return Token 列表
     */
    public List<String> getTokensByCertSerialNo(String certSerialNo) {
        List<String> tokens = new ArrayList<>();
        for (java.util.Map.Entry<String, String> entry : tokenMap.entrySet()) {
            if (certSerialNo.equals(entry.getValue())) {
                tokens.add(entry.getKey());
            }
        }
        return tokens;
    }
}
