package com.publishgateway.udpproxy.assembly;

/**
 * UDP 文件重组状态。
 */
public enum FileAssemblyStatus {

    /** 当前包不是可识别的文件分片包。 */
    IGNORED,

    /** 当前文件仍在收集中。 */
    ASSEMBLING,

    /** 已重组成完整文件。 */
    COMPLETED,

    /** 当前文件被丢弃，例如超过大小限制或缓存超时。 */
    DISCARDED,

    /** 解析或重组异常。 */
    FAILED
}
