package com.monitorplatform.alarm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.monitorplatform.alarm.entity.AlarmRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 告警记录Mapper
 */
@Mapper
public interface AlarmMapper extends BaseMapper<AlarmRecord> {


    /**
     * 统计本周每天各级别告警数量
     */
    @Select("SELECT DAYOFWEEK(alarm_time) as day_of_week,alarm_level,COUNT(*) as count " +
            "FROM alarm_record " +
            "WHERE alarm_time >= DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY) " +
            "AND alarm_time < DATE_ADD(DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY), INTERVAL 7 DAY) " +
            "GROUP BY DAYOFWEEK(alarm_time), alarm_level " +
            "ORDER BY day_of_week, alarm_level")
    List<Map<String, Object>> countWeeklyByLevel();

    /**
     * 按类型统计告警数量
     */
    @Select("SELECT alarm_type, COUNT(*) as count FROM alarm_record GROUP BY alarm_type")
    List<Map<String, Object>> countByType();
    
    /**
     * 按级别统计告警数量
     */
    @Select("SELECT alarm_level, COUNT(*) as count FROM alarm_record GROUP BY alarm_level")
    List<Map<String, Object>> countByLevel();
    
    /**
     * 统计待处理告警数量
     */
    @Select("SELECT COUNT(*) FROM alarm_record WHERE handle_status = 'pending'")
    Long countPending();
    
    /**
     * 统计已处理告警数量
     */
    @Select("SELECT COUNT(*) FROM alarm_record WHERE handle_status = 'processed'")
    Long countProcessed();
    
    /**
     * 统计严重级别告警数量
     */
    @Select("SELECT COUNT(*) FROM alarm_record WHERE alarm_level = 'critical'")
    Long countCritical();
    
    /**
     * 统计重要级别告警数量
     */
    @Select("SELECT COUNT(*) FROM alarm_record WHERE alarm_level = 'serious'")
    Long countSerious();
    
    /**
     * 统计今日告警总数
     */
    @Select("SELECT COUNT(*) FROM alarm_record WHERE DATE(alarm_time) = CURDATE()")
    Long countToday();
    
    /**
     * 统计昨日告警总数
     */
    @Select("SELECT COUNT(*) FROM alarm_record WHERE DATE(alarm_time) = DATE_SUB(CURDATE(), INTERVAL 1 DAY)")
    Long countYesterday();
    
    /**
     * 统计今日紧急告警数（critical + serious）
     */
    @Select("SELECT COUNT(*) FROM alarm_record WHERE DATE(alarm_time) = CURDATE() AND alarm_level IN ('critical', 'serious')")
    Long countUrgentToday();
    
    /**
     * 统计昨日紧急告警数
     */
    @Select("SELECT COUNT(*) FROM alarm_record WHERE DATE(alarm_time) = DATE_SUB(CURDATE(), INTERVAL 1 DAY) AND alarm_level IN ('critical', 'serious')")
    Long countUrgentYesterday();
    
    /**
     * 统计今日已处理数
     */
    @Select("SELECT COUNT(*) FROM alarm_record WHERE DATE(handle_time) = CURDATE() AND handle_status = 'processed'")
    Long countProcessedToday();
    
    /**
     * 统计昨日已处理数
     */
    @Select("SELECT COUNT(*) FROM alarm_record WHERE DATE(handle_time) = DATE_SUB(CURDATE(), INTERVAL 1 DAY) AND handle_status = 'processed'")
    Long countProcessedYesterday();

    /**
     * 统计指定链路下未处理告警数量
     */
    @Select("SELECT COUNT(*) FROM alarm_record WHERE chain_id = #{chainId} AND handle_status = 'pending'")
    Long countPendingByChainId(@Param("chainId") Long chainId);

    /**
     * 统计今日未处理告警数量（按 alarm_time）
     */
    @Select("SELECT COUNT(*) FROM alarm_record WHERE DATE(alarm_time) = CURDATE() AND handle_status = 'pending'")
    Long countPendingToday();

    /**
     * 统计今日已处置告警数量（按 alarm_time，排除误报）
     */
    @Select("SELECT COUNT(*) FROM alarm_record WHERE DATE(alarm_time) = CURDATE() AND handle_status = 'processed'")
    Long countHandledToday();

    /**
     * 统计今日误报告警数量（按 alarm_time）
     */
    @Select("SELECT COUNT(*) FROM alarm_record WHERE DATE(alarm_time) = CURDATE() AND handle_status = 'false_alarm'")
    Long countFalseAlarmToday();

    /**
     * 按情报板 IP 查询待处理告警列表（用于黑屏处置时全量批量处置）
     * 按告警时间倒序排序，最新的先处置
     */
    @Select("SELECT * FROM alarm_record WHERE board_ip = #{boardIp} AND handle_status = 'pending' ORDER BY alarm_time DESC")
    List<AlarmRecord> selectPendingByBoardIp(@Param("boardIp") String boardIp);
}