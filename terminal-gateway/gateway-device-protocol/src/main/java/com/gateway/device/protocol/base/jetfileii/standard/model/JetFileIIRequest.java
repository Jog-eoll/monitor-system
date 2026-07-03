package com.gateway.device.protocol.base.jetfileii.standard.model;

import lombok.Builder;
import lombok.Data;

/**
 * JetFileII 命令请求对象 —— Codec 的输入，封装 mainCmd/subCmd/arg/data/flag。
 */
@Data
@Builder
public class JetFileIIRequest {

    /**
     * 大类命令
     */
    private byte mainCmd;

    /**
     * 小类命令
     */
    private byte subCmd;

    /**
     * 参数段 (Arg)
     */
    private byte[] arg;

    /**
     * 数据段 (Data)
     */
    private byte[] data;

    /**
     * 是否需要回送
     */
    private boolean needReply;

    /**
     * 是否使用 CRC 校验
     */
    private boolean useCrc;

    /**
     * 是否广播
     */
    private boolean broadcast;

    /**
     * 目标 GG 地址
     */
    private int destGg;

    /**
     * 目标 UU 地址
     */
    private int destUu;

    // ════════════════════════════════════════════════════
    // 便捷工厂
    // ════════════════════════════════════════════════════

    public static JetFileIIRequest of(byte mainCmd, byte subCmd) {
        return JetFileIIRequest.builder()
                .mainCmd(mainCmd).subCmd(subCmd)
                .needReply(true).build();
    }

    public static JetFileIIRequest of(byte mainCmd, byte subCmd, byte[] arg, byte[] data) {
        return JetFileIIRequest.builder()
                .mainCmd(mainCmd).subCmd(subCmd)
                .arg(arg).data(data)
                .needReply(true).build();
    }

    /**
     * 设置为广播（GG=0, UU=0）并返回自身
     */
    public JetFileIIRequest broadcast(boolean b) {
        this.broadcast = b;
        if (b) {
            this.destGg = 0;
            this.destUu = 0;
        }
        return this;
    }

    /**
     * 设置为无需回送并返回自身
     */
    public JetFileIIRequest noReply() {
        this.needReply = false;
        return this;
    }
}
