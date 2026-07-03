package com.monitorplatform.alarm.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * Forward 服务 Feign 客户端
 * 用于查询链路中情报板的 IP 地址
 */
@FeignClient(name = "monitor-forward")
public interface ForwardFeignClient {

    /**
     * 通过链路 ID 查询情报板 IP
     * 对应 forward 服务：GET /chain/info-board?chainId=xxx
     *
     * @param chainId 链路 ID
     * @return { code, data: { infoBoardIp, infoBoardPort, ... } }
     */
    @GetMapping("/chain/info-board")
    Map<String, Object> getInfoBoardByChainId(@RequestParam("chainId") Long chainId);

    /**
     * 通过情报板 IP 查询所属链路信息（含 chainId）
     * 对应 forward 服务：GET /chain/gateway-by-board?boardIp=xxx
     *
     * @param boardIp 情报板 IP
     * @return { code, data: { chainId, boardIp, terminalGatewayIp, ... } }
     */
    @GetMapping("/chain/gateway-by-board")
    Map<String, Object> getGatewayByBoardIp(@RequestParam("boardIp") String boardIp);
}
