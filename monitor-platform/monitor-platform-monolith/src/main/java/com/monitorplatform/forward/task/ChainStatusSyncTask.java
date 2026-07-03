package com.monitorplatform.forward.task;

import com.monitorplatform.forward.service.TaskChainService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 任务链路状态同步与健康度巡检定时任务
 */
@Slf4j
@Component
public class ChainStatusSyncTask {

    @Autowired
    private TaskChainService taskChainService;

    /**
     * 定时同步所有链路状态
     * 每隔1小时执行一次
     */
    @Scheduled(fixedRate = 3600000)
    public void syncAllChainStatus() {
        try {
            log.debug("开始执行链路状态同步任务");
            Integer count = taskChainService.syncAllChainStatus();
            log.debug("链路状态同步任务完成，成功同步: {} 条", count);
        } catch (Exception e) {
            log.error("链路状态同步任务执行失败", e);
        }
    }

    /**
     * 定时链路健康度巡检
     * 每隔10分钟执行一次，对所有启用链路进行健康度检查
     * 及时发现离线/异常节点并更新健康度评分
     */
    @Scheduled(fixedRate = 600000, initialDelay = 60000)
    public void healthCheckAllChains() {
        try {
            log.info("开始执行链路健康度巡检任务");
            Integer count = taskChainService.checkAllChainHealth();
            log.info("链路健康度巡检任务完成，成功检查: {} 条", count);
        } catch (Exception e) {
            log.error("链路健康度巡检任务执行失败", e);
        }
    }
}
