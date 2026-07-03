package com.infopublish.client.service.impl;

import com.infopublish.client.service.ProcessBindService;
import com.infopublish.client.service.TrafficMonitorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 流量来源合法性校验服务实现
 *
 * <p>不再统计流量数量，改为校验每次数据接收事件的来源合法性。
 * 通过 ProcessBindService 获取由进程 PID 反查的合法 IP:Port 白名单，
 * 若来源不在白名单中则立即生成告警，提示运维人员拔出 UKey。
 *
 * <p>告警生成后通过 getAlertMessage() 供前端轮询；
 * 用户确认后调用 clearAlert() 清除。
 */
@Slf4j
@Service
public class TrafficMonitorServiceImpl implements TrafficMonitorService {

    @Resource
    private ProcessBindService processBindService;

    /**
     * 当前未读告警（null 表示无告警）
     * volatile 保证多线程可见性
     */
    private volatile AlertMessage currentAlert = null;

    // ========== 接口实现 ==========

    @Override
    public boolean checkAndRecordSource(String sourceIp, int sourcePort) {
        boolean authorized = processBindService.isEndpointAuthorized(sourceIp, sourcePort);
        if (!authorized) {
            String msg = String.format(
                    "检测到疑似非法数据来源：%s:%d，该 IP:Port 不在授权白名单中，建议立即拔出 UKey！",
                    sourceIp, sourcePort);
            log.warn("[流量来源校验] {}", msg);
            // 每次非法来源事件都更新告警（保留最新一条）
            currentAlert = new AlertMessage(msg, System.currentTimeMillis(), sourceIp, sourcePort);
        } else {
            log.debug("[流量来源校验] 合法来源 {}:{} 通过校验", sourceIp, sourcePort);
        }
        return authorized;
    }

    @Override
    public AlertMessage getAlertMessage() {
        return currentAlert;
    }

    @Override
    public void clearAlert() {
        currentAlert = null;
        log.info("[流量来源校验] 告警已由用户确认并清除");
    }
}
