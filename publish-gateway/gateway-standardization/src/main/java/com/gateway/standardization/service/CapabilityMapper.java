package com.gateway.standardization.service;

/**
 * 业务动作映射器
 * <p>
 * 根据协议类型、内容类型和命令类型，映射为中文业务动作名称（action）。
 * action 是对外契约字段，Sigma 团队和管控平台通过此字段理解消息含义。
 * 解密网关内部再通过 ActionMapper 将 action 翻译为 DeviceCapability，
 * 并根据目标设备厂商路由到青松、卡莱特等厂商适配器指令。
 * </p>
 */
public interface CapabilityMapper {

    /**
     * 映射业务动作
     *
     * @param protocol    协议标识（如 JetFileII-Type1, Sigma-FileTransfer, Nova 等）
     * @param contentType 内容类型（如 image, video, text, binary）
     * @param commandType 命令类型（如 SEND_FILE, FILE_PLAY, BRIGHTNESS 等）
     * @return 中文业务动作名称（如"设置播放列表"、"调节亮度"），无法映射时返回 null
     */
    String mapAction(String protocol, String contentType, String commandType);
}
