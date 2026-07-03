package com.monitorplatform.content.feign;

import com.monitorplatform.content.entity.vo.BoardGatewayDataVO;
import com.monitorplatform.content.entity.vo.CommonServiceResponseVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "monitor-forward")
public interface ForwardFeignClient {

    @GetMapping("/chain/gateway-by-board")
    CommonServiceResponseVO<BoardGatewayDataVO> getGatewayByBoardIp(@RequestParam("boardIp") String boardIp);
}
