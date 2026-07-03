package com.gateway.device.core.controller.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 批量指令 HTTP 请求体
 * <p>
 * 对外接口使用中文业务动作（action），如"设置播放列表"、"调节亮度"。
 * 解密网关内部通过 {@code ActionMapper} 将 action 翻译为 {@code DeviceCapability}，
 * 再路由到对应的 JetFileII 二进制指令处理器。
 * </p>
 *
 * <p>示例请求：
 * <pre>
 * {
 *   "requestId": "req-001",
 *   "action": "设置播放列表",
 *   "target": { "ip": "192.168.1.100" },
 *   "params": { "paths": ["D:\\P\\show.nmg"] }
 * }
 * </pre>
 * </p>
 */
@Data
public class BatchCommandRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 请求 ID（业务层幂等键） */
    private String requestId;

    /**
     * 业务动作（对外契约字段）
     * <p>
     * Sigma / 管控平台传入的中文动作名称，如"设置播放列表"、"调节亮度"、"黑屏"等。
     * 解密网关内部通过 ActionMapper 翻译为 DeviceCapability 枚举后路由到对应 Handler。
     * </p>
     */
    private String action;

    /** 目标设备信息 */
    private TargetRef target;

    /** 内容信息（标准化 JSON 中的 content 字段） */
    private ContentInfo content;

    /** 指令参数（如 {on: true}、{brightness: 80}、{paths: [...]}） */
    private Map<String, Object> params;

    /**
     * 目标设备引用
     * <p>
     * 映射到 {@code DeviceSelector} 的多维度筛选条件。
     * 目前以 deviceId（设备 IP 或注册 ID）为主键匹配，其余维度按需扩展。
     * </p>
     */
    @Data
    public static class TargetRef implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 设备标识（通常为设备注册 ID 或 IP） */
        private String deviceId;

        /** 设备 IP */
        private String ip;

        /** 设备端口 */
        private Integer port;

        /** 指定厂商（可选） */
        private String vendor;

        /** 指定产品类型（可选） */
        private String productType;

        /** 指定分组 ID（可选） */
        private String groupId;

        /** 仅在线设备，默认 true */
        private Boolean onlineOnly;
    }

    /**
     * 内容信息
     * <p>
     * 文件上传类动作可通过 content.data 传入 Base64 文件字节，控制器会转换为
     * Handler 需要的 params.data。
     * </p>
     */
    @Data
    public static class ContentInfo implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 文件名，如 notice.nmg、poster.jpg */
        private String fileName;

        /** 文件类型/扩展名，如 nmg、pmg、qst、image、video、text */
        private String fileType;

        /** 内容数据：文件上传时为 Base64；纯文本上传时可为原始文本 */
        private String data;

        /** 大文件对象存储路径，当前 HTTP 入口仅透传，不直接拉取 */
        private String minioPath;

        /** 图片格式，如 jpg、png、gif、bmp */
        private String imageFormat;
    }
}
