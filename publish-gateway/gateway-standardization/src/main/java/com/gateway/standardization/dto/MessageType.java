package com.gateway.standardization.dto;

/**
 * 标准化消息类型枚举
 * <p>
 * 对应 Sigma 对接文档中加密端需补齐的 messageType 字段。
 * </p>
 */
public enum MessageType {

    /** 控制指令（亮度/开关屏/校时等） */
    COMMAND,

    /** 完整内容（图片/文本/视频等） */
    CONTENT,

    /** 内容分片（大文件分包传输中的单个片段） */
    CONTENT_FRAGMENT,

    /** 播放列表设置指令 */
    PLAYLIST,

    /** 无法解析的原始包 */
    RAW_PACKET;
}
