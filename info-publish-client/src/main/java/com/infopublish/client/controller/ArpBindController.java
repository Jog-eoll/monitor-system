package com.infopublish.client.controller;

import com.infopublish.client.common.Result;
import com.infopublish.client.service.ArpBindService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * ARP 静态绑定控制器
 *
 * 用于将"情报板IP"静态绑定到"发布网关MAC"，
 * 实现本机流量重定向（ARP 欺骗策略）。
 * 程序必须以管理员权限运行。
 */
@Slf4j
@RestController
@RequestMapping("/api/arp")
@CrossOrigin(origins = "*")
public class ArpBindController {

    @Resource
    private ArpBindService arpBindService;

    /**
     * 执行静态 ARP 绑定
     * POST /api/arp/bind
     * Body: { "targetIp": "192.168.1.50", "gatewayMac": "00-30-b4-01-62-98" }
     */
    @PostMapping("/bind")
    public Result<Object> bind(@RequestBody Map<String, String> body) {
        String targetIp   = body.get("targetIp");
        String gatewayMac = body.get("gatewayMac");
        if (targetIp == null || targetIp.trim().isEmpty()) {
            return Result.build(400, "targetIp 不能为空", null);
        }
        if (gatewayMac == null || gatewayMac.trim().isEmpty()) {
            return Result.build(400, "gatewayMac 不能为空", null);
        }
        try {
            String msg = arpBindService.bind(targetIp.trim(), gatewayMac.trim());
            boolean success = msg.startsWith("绑定成功");
            return success ? Result.ok(msg) : Result.build(500, msg, null);
        } catch (Exception e) {
            log.error("[ARP] bind 异常", e);
            return Result.error(500, "ARP绑定异常: " + e.getMessage());
        }
    }

    /**
     * 解除静态 ARP 绑定
     * POST /api/arp/unbind
     * Body: { "targetIp": "192.168.1.50" }
     */
    @PostMapping("/unbind")
    public Result<Object> unbind(@RequestBody Map<String, String> body) {
        String targetIp = body.get("targetIp");
        if (targetIp == null || targetIp.trim().isEmpty()) {
            return Result.build(400, "targetIp 不能为空", null);
        }
        try {
            String msg = arpBindService.unbind(targetIp.trim());
            return Result.ok(msg);
        } catch (Exception e) {
            log.error("[ARP] unbind 异常", e);
            return Result.error(500, "ARP解绑异常: " + e.getMessage());
        }
    }

    /**
     * 查询当前 ARP 绑定状态
     * GET /api/arp/status
     */
    @GetMapping("/status")
    public Result<Object> status() {
        try {
            ArpBindService.ArpStatus s = arpBindService.getStatus();
            return Result.ok(s);
        } catch (Exception e) {
            return Result.error(500, "查询状态失败");
        }
    }

    /**
     * 列出本机所有网卡接口（供参考）
     * GET /api/arp/interfaces
     */
    @GetMapping("/interfaces")
    public Result<Object> interfaces() {
        try {
            List<ArpBindService.InterfaceInfo> list = arpBindService.listInterfaces();
            return Result.ok(list);
        } catch (Exception e) {
            return Result.error(500, "获取网卡列表失败");
        }
    }


    /**
     * 查询
     *
     * @return
     */

    @PostMapping("/networkQuery")
    public Result<?> networkQuery() {
        return Result.ok(arpBindService.findInterfaceIndex());
    }
}
