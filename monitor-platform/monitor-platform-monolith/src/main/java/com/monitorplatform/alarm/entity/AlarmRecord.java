package com.monitorplatform.alarm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警记录实体
 */
@Data
@TableName("alarm_record")
public class AlarmRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String alarmType;

    private String alarmLevel;

    private Long chainId;

    /** 情报板IP（持久化字段，由上报时携带） */
    private String boardIp;

    /** 情报板端口（持久化字段，由上报时携带） */
    private Integer boardPort;

    private String deviceId;

    private String deviceName;

    private String contentId;

    private String violationType;

    private String violationDetail;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime alarmTime;

    private String handleStatus;

    private String handleOperator;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime handleTime;

    private String handleRemark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createTime;

    // ==================== 违规内容快照（持久化字段，告警上报时冗余存储） ====================

    /** 违规内容类型：text/image/video */
    private String contentType;

    /** 违规文本内容（text时填充） */
    private String contentData;

    /** 违规内容访问URL（image/video时填充MinIO完整URL） */
    private String contentFileUrl;

    // ==================== 非数据库字段（查询时动态获取） ====================

    /**
     * 情报板IP（非数据库字段，查询时动态获取）
     */
    @TableField(exist = false)
    private String infoBoardIp;

    /**
     * 情报板端口（非数据库字段，查询时动态获取，默认9520）
     */
    @TableField(exist = false)
    private Integer infoBoardPort;

    /**
     * 情报板经度（非数据库字段，推送时通过 chainId → device 服务动态获取）
     */
    @TableField(exist = false)
    private String longitude;

    /**
     * 情报板纬度（非数据库字段，推送时通过 chainId → device 服务动态获取）
     */
    @TableField(exist = false)
    private String latitude;
}
