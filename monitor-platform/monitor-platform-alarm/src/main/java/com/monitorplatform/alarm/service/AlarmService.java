package com.monitorplatform.alarm.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitorplatform.alarm.entity.AlarmRecord;
import com.monitorplatform.alarm.entity.dto.*;
import com.monitorplatform.alarm.entity.vo.AlarmTodayStatsVO;
import com.monitorplatform.alarm.entity.vo.GatewayActionResultVO;
import com.monitorplatform.alarm.entity.vo.WeeklyAlarmChartVO;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * 告警服务接口
 */
public interface AlarmService {
    
    /**
     * 接收告警
     */
    AlarmRecord receiveAlarm(AlarmReceiveDTO dto);
    
    /**
     * 处理告警
     */
    boolean handleAlarm(AlarmHandleDTO dto);
    
    /**
     * 分页查询告警
     */
    Page<AlarmRecord> pageQuery(AlarmQueryDTO dto);
    
    /**
     * 查询告警详情
     */
    AlarmRecord getById(Long id);
    
    /**
     * 查询最近告警列表
     */
    List<AlarmRecord> getRecentList(Integer limit);
    
    /**
     * 查询待处理告警列表
     */
    List<AlarmRecord> getPendingList(Integer limit);
    
    /**
     * 告警统计
     */
    AlarmStatisticsDTO statistics();
    
    /**
     * 导出告警记录
     */
    void exportExcel(HttpServletResponse response, AlarmQueryDTO dto);
    
    /**
     * 删除告警记录
     */
    boolean delete(Long id);
    
    /**
     * 批量删除
     */
    int deleteBatch(List<Long> ids);
    
    /**
     * 仪表盘概览
     */
    DashboardOverviewDTO getDashboardOverview();

    /**
     * 自动黑屏：仅发送黑屏指令，不改变告警处置状态和监看业务状态。
     */
    GatewayActionResultVO sendAutoBlackScreenCommand(DisconnectGatewayDTO dto);

    GatewayActionResultVO disconnectGatewayConnection(DisconnectGatewayDTO dto);

    /**
     * 应急恢复：向情报板发送停止黑屏指令
     */
    GatewayActionResultVO resumeGatewayConnection(DisconnectGatewayDTO dto);

    /**
     * 本周每天各级别告警统计
     */
    WeeklyAlarmChartVO getWeeklyAlarmChart();

    /**
     * 查询指定情报板IP是否存在待处理告警
     */
    boolean hasPendingAlarmByIp(String ip);

    /**
     * 今日告警统计：总数、已处置、未处理
     */
    AlarmTodayStatsVO getTodayStats();

    /**
     * 标记告警为误报
     */
    boolean markFalseAlarm(FalseAlarmDTO dto);
}
