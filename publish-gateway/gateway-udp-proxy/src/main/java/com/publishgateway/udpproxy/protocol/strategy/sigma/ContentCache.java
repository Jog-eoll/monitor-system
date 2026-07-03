package com.publishgateway.udpproxy.protocol.strategy.sigma;

import com.alibaba.fastjson.JSON;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 内容缓存组件（基于 Redis）
 *
 * 首次截取的内容信息按 filePath 缓存到 Redis，后续 FILE_PLAY 播放指令命中缓存时
 * 直接复用首次的 minioPath / data 重新构建上报数据，确保每次发送都能被截取并送检。
 *
 * 缓存策略：
 * - key:   publish-gateway:content-cache:{filePath}  （如 D:\p\111.jpg）
 * - value: JSON 序列化的 CacheEntry（contentType + minioPath + imageFormat + data）
 * - TTL:   7 天（可调整）
 *
 * @author zyh
 */
@Slf4j
@Component
public class ContentCache {

    private static final String KEY_PREFIX = "publish-gateway:content-cache:";
    private static final String FRESH_KEY_PREFIX = "publish-gateway:content-fresh:";
    private static final String TRANSFERRED_KEY_PREFIX = "publish-gateway:transferred:";
    private static final long DEFAULT_TTL_DAYS = 7;
    /** 刚传输标记有效期：10 秒内的 FILE_PLAY 被认为同一次发送的配套播放，跳过重复上报 */
    private static final long FRESH_TTL_SECONDS = 10;
    /**
     * 文件传输已上报标记有效期：60 秒。
     * 在此窗口内到来的 FILE_PLAY 展开时会跳过该文件，避免与文件传输路径重复上报。
     */
    private static final long TRANSFERRED_TTL_SECONDS = 60;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 写入缓存
     *
     * @param filePath    文件路径（如 D:\p\111.jpg），作为缓存 key
     * @param contentType 内容类型（image / video / text / file_reference）
     * @param minioPath   MinIO 对象路径（图片/视频用，文字类型为 null）
     * @param imageFormat 图片格式（JPEG/PNG 等，非图片类型为 null）
     * @param data        文字内容（text 类型用，图片/视频为 null）
     */
    public void put(String filePath, String contentType, String minioPath, String imageFormat, String data) {
        try {
            CacheEntry entry = new CacheEntry(contentType, minioPath, imageFormat, data);
            redisTemplate.opsForValue().set(
                    KEY_PREFIX + filePath, JSON.toJSONString(entry),
                    DEFAULT_TTL_DAYS, TimeUnit.DAYS);
            // 写入「刚传输」短效标记：30秒内的 FILE_PLAY 认为是同一次发送的配套播放指令，跳过重复上报
            redisTemplate.opsForValue().set(FRESH_KEY_PREFIX + filePath, "1", FRESH_TTL_SECONDS, TimeUnit.SECONDS);
            log.debug("【内容缓存】写入成功: filePath={}, contentType={}", filePath, contentType);
        } catch (Exception e) {
            // 缓存写入失败不影响主流程
            log.warn("【内容缓存】写入失败（不影响主流程）: filePath={}, error={}", filePath, e.getMessage());
        }
    }

    /**
     * 判断该文件是否「刚传输」（即文件传输后 30 秒内）
     * 用于过滤同一次发送中紧随文件传输的配套 FILE_PLAY 指令，避免重复上报
     */
    public boolean isFresh(String filePath) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(FRESH_KEY_PREFIX + filePath));
        } catch (Exception e) {
            log.warn("【内容缓存】刷新检查失败（降级为非刚传输）: filePath={}, error={}", filePath, e.getMessage());
            return false;
        }
    }

    /**
     * 读取缓存
     *
     * @param filePath 文件路径
     * @return 缓存条目，未命中返回 null
     */
    public CacheEntry get(String filePath) {
        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + filePath);
            if (json != null) {
                return JSON.parseObject(json, CacheEntry.class);
            }
        } catch (Exception e) {
            // 缓存读取失败不影响主流程，降级为未命中
            log.warn("【内容缓存】读取失败（降级为未命中）: filePath={}, error={}", filePath, e.getMessage());
        }
        return null;
    }

    /**
     * 标记某个文件已由文件传输路径上报（60s 有效期）
     * FILE_PLAY 展开伴随文件时，若此标记存在则跳过该文件，避免重复上报。
     */
    public void markTransferred(String filePath) {
        if (filePath == null) return;
        try {
            redisTemplate.opsForValue().set(TRANSFERRED_KEY_PREFIX + filePath, "1",
                    TRANSFERRED_TTL_SECONDS, TimeUnit.SECONDS);
            log.debug("【传输标记】写入: filePath={}", filePath);
        } catch (Exception e) {
            log.warn("【传输标记】写入失败: filePath={}, error={}", filePath, e.getMessage());
        }
    }

    /**
     * 判断某个文件是否在 60s 内已由文件传输路径上报过
     */
    public boolean isTransferred(String filePath) {
        if (filePath == null) return false;
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(TRANSFERRED_KEY_PREFIX + filePath));
        } catch (Exception e) {
            log.warn("【传输标记】检查失败（降级为未传输）: filePath={}, error={}", filePath, e.getMessage());
            return false;
        }
    }

    /**
     * 缓存条目
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CacheEntry {
        /** 内容类型：image / video / text / file_reference */
        private String contentType;
        /** MinIO 对象路径（图片/视频用） */
        private String minioPath;
        /** 图片格式：JPEG / PNG / GIF / BMP（图片用） */
        private String imageFormat;
        /** 文字内容（text 类型用） */
        private String data;
    }
}
