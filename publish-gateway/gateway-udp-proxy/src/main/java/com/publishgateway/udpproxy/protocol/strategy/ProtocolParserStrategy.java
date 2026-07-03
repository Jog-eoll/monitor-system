package com.publishgateway.udpproxy.protocol.strategy;

import com.publishgateway.udpproxy.protocol.strategy.context.ParseContext;
import com.publishgateway.udpproxy.protocol.strategy.context.ParseResult;

/**
 * 协议解析策略接口
 * 不同厂家的情报板实现此接口，提供各自的协议解析逻辑
 */
public interface ProtocolParserStrategy {

    /**
     * 该策略支持的厂家标识（如 "sigma", "ledman"）
     */
    String supportedManufacturer();

    /**
     * 解析 UDP/TCP 数据，返回解析结果
     *
     * @param data    原始数据
     * @param context 解析上下文（ruleId, chainId, sourceIp 等辅助信息）
     * @return 解析结果，包含协议数据体和元信息
     */
    ParseResult parse(byte[] data, ParseContext context);
}
