package com.monitorplatform.forward.entity.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 链路健康度检查结果VO
 */
@Data
public class ChainHealthVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 链路ID
     */
    private Long chainId;

    /**
     * 链路名称
     */
    private String chainName;

    /**
     * 链路编码
     */
    private String chainCode;

    /**
     * 健康度评分（0-100分）
     * 评分规则：
     * - 100分：所有节点在线
     * - 0分：所有节点离线或异常
     * - 按在线节点比例计算，异常节点扣分更重
     */
    private Integer healthScore;

    /**
     * 健康等级: A(优秀>=90) / B(良好>=70) / C(一般>=50) / D(较差>=30) / F(故障<30)
     */
    private String healthLevel;

    /**
     * 链路状态: 0-离线/不可用, 1-在线/可用, 2-部分可用
     */
    private Integer status;

    /**
     * 总节点数
     */
    private Integer totalNodes;

    /**
     * 在线节点数
     */
    private Integer onlineNodes;

    /**
     * 离线节点数
     */
    private Integer offlineNodes;

    /**
     * 异常节点数
     */
    private Integer errorNodes;

    /**
     * 核心节点在线数
     */
    private Integer coreOnlineNodes;

    /**
     * 核心节点总数
     */
    private Integer coreTotalNodes;

    /**
     * 不健康节点列表（离线/异常）
     */
    private List<UnhealthyNodeInfo> unhealthyNodes;

    /**
     * 健康检查时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime checkTime;

    /**
     * 健康检查耗时（毫秒）
     */
    private Long checkDurationMs;

    /**
     * 是否启用
     */
    private Integer enabled;

    /**
     * 不健康节点信息
     */
    @Data
    public static class UnhealthyNodeInfo implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 节点ID
         */
        private Long nodeId;

        /**
         * 设备ID
         */
        private String deviceId;

        /**
         * 设备名称
         */
        private String deviceName;

        /**
         * 设备类型
         */
        private String deviceType;

        /**
         * 设备类型描述
         */
        private String deviceTypeDesc;

        /**
         * 设备IP
         */
        private String deviceIp;

        /**
         * 节点状态: 离线/异常
         */
        private String nodeStatus;

        /**
         * 是否为核心节点
         */
        private Boolean coreNode;

        /**
         * 状态描述（例如：设备无响应、连接超时等）
         */
        private String statusDesc;

        // ==================== 异常节点详情（仅 nodeStatus=异常 时填充） ====================

        /**
         * 异常详情（具体错误信息，如：设备告警、固件异常等）
         */
        private String errorDetail;

        /**
         * 告警次数（来自设备服务）
         */
        private Integer alarmCount;

        /**
         * 最后告警时间（来自设备服务，格式：yyyy-MM-dd HH:mm:ss）
         */
        private String lastAlarmTime;

        /**
         * 最后告警类型（来自设备服务）
         */
        private String lastAlarmType;
    }
}
