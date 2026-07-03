package com.gateway.device.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 字体管理公共配置 —— 协议无关，所有厂商通用。
 *
 * <p>{@code localPath} 为字体文件根目录，字体同步时递归扫描该目录下所有文件
 * 自动发现可用字体，无需显式枚举列表。</p>
 *
 * <pre>
 * device:
 *   fonts:
 *     local-path: "./fonts"
 * </pre>
 */
@Setter
@Getter
@ConfigurationProperties("device.fonts")
public class FontsProperties {

    /**
     * 本地字库文件根目录（外部文件系统路径），支持嵌套子目录。
     */
    private String localPath = "./fonts";
}
