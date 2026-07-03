package com.monitorplatform.alarm.feign;

import com.monitorplatform.forward.service.TaskChainService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * Forward 服务本地调用（替代原 Feign 客户端）
 */
@Slf4j
@Service("alarmForwardFeignClient")
public class ForwardFeignClient {

    @Resource
    private TaskChainService taskChainService;

    /**
     * 通过链路 ID 查询情报板 IP
     */
    public Map<String, Object> getInfoBoardByChainId(Long chainId) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> data = taskChainService.getInfoBoardByChainId(chainId);
            result.put("code", Boolean.TRUE.equals(data.get("success")) ? 200 : 400);
            result.put("data", data);
        } catch (Exception e) {
            log.error("查询情报板IP失败: chainId={}", chainId, e);
            result.put("code", 500);
            result.put("msg", "查询失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 通过情报板 IP 查询所属链路信息
     */
    public Map<String, Object> getGatewayByBoardIp(String boardIp) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> data = taskChainService.getTerminalGatewayByBoardIp(boardIp);
            result.put("code", Boolean.TRUE.equals(data.get("success")) ? 200 : 400);
            result.put("data", data);
        } catch (Exception e) {
            log.error("查询终端网关失败: boardIp={}", boardIp, e);
            result.put("code", 500);
            result.put("msg", "查询失败: " + e.getMessage());
        }
        return result;
    }
}
