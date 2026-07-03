package com.monitorplatform.content.feign;

import com.monitorplatform.content.entity.vo.BoardGatewayDataVO;
import com.monitorplatform.content.entity.vo.CommonServiceResponseVO;
import com.monitorplatform.forward.service.TaskChainService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Map;

/**
 * Forward 服务本地调用（替代原 Feign 客户端）
 */
@Slf4j
@Service("contentForwardFeignClient")
public class ForwardFeignClient {

    @Resource
    private TaskChainService taskChainService;

    public CommonServiceResponseVO<BoardGatewayDataVO> getGatewayByBoardIp(String boardIp) {
        CommonServiceResponseVO<BoardGatewayDataVO> resp = new CommonServiceResponseVO<>();
        try {
            Map<String, Object> result = taskChainService.getTerminalGatewayByBoardIp(boardIp);
            if (Boolean.TRUE.equals(result.get("success"))) {
                BoardGatewayDataVO vo = new BoardGatewayDataVO();
                if (result.get("chainId") != null) {
                    vo.setChainId(Long.valueOf(result.get("chainId").toString()));
                }
                vo.setBoardIp(boardIp);
                if (result.get("terminalGatewayIp") != null) {
                    vo.setTerminalGatewayIp(result.get("terminalGatewayIp").toString());
                }
                resp.setCode(200);
                resp.setData(vo);
            } else {
                resp.setCode(400);
                resp.setMsg(String.valueOf(result.getOrDefault("message", "查询失败")));
            }
        } catch (Exception e) {
            log.error("查询终端网关失败: boardIp={}", boardIp, e);
            resp.setCode(500);
            resp.setMsg("查询失败: " + e.getMessage());
        }
        return resp;
    }
}
