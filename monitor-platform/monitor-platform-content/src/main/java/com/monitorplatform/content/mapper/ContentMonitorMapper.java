package com.monitorplatform.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.monitorplatform.content.entity.ContentMonitor;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 内容监看Mapper
 */
@Mapper
public interface ContentMonitorMapper extends BaseMapper<ContentMonitor> {

    /**
     * 根据内容ID查询
     */
    @Select("SELECT * FROM t_content_monitor WHERE content_id = #{contentId}")
    ContentMonitor selectByContentId(@Param("contentId") String contentId);

    /**
     * 查询同一 MinIO 对象最近一次检测记录，用于避免同一文件被重复审核、重复告警。
     */
    @Select("SELECT * FROM t_content_monitor WHERE minio_path = #{minioPath} AND content_type = #{contentType} " +
            "ORDER BY create_time DESC, id DESC LIMIT 1")
    ContentMonitor selectLatestByMinioPathAndType(@Param("minioPath") String minioPath,
                                                  @Param("contentType") String contentType);

    /**
     * 查询最近的内容记录（分页）
     */
    @Select("SELECT * FROM t_content_monitor ORDER BY create_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<ContentMonitor> selectRecentWithPage(@Param("limit") int limit, @Param("offset") int offset);

    /**
     * 查询总记录数
     */
    @Select("SELECT COUNT(*) FROM t_content_monitor")
    int selectTotalCount();

    /**
     * 查询最近的内容记录
     */
    @Select("SELECT * FROM t_content_monitor ORDER BY create_time DESC LIMIT #{limit}")
    List<ContentMonitor> selectRecent(@Param("limit") int limit);

    /**
     * 查询违规内容
     */
    @Select("SELECT * FROM t_content_monitor WHERE is_violation = 1 ORDER BY create_time DESC LIMIT #{limit}")
    List<ContentMonitor> selectViolations(@Param("limit") int limit);

    /**
     * 更新识别结果
     */
    @Update("UPDATE t_content_monitor SET " +
            "is_violation = #{isViolation}, " +
            "violation_type = #{violationType}, " +
            "confidence = #{confidence}, " +
            "keywords = #{keywords}, " +
            "status = #{status}, " +
            "recognition_time = NOW(), " +
            "update_time = NOW() " +
            "WHERE content_id = #{contentId}")
    int updateRecognitionResult(@Param("contentId") String contentId,
                                @Param("isViolation") Integer isViolation,
                                @Param("violationType") String violationType,
                                @Param("confidence") Double confidence,
                                @Param("keywords") String keywords,
                                @Param("status") String status);

    /**
     * 更新状态
     */
    @Update("UPDATE t_content_monitor SET status = #{status}, handle_time = NOW(), handle_by = #{handleBy}, update_time = NOW() WHERE content_id = #{contentId}")
    int updateStatus(@Param("contentId") String contentId, 
                     @Param("status") String status,
                     @Param("handleBy") String handleBy);

    /**
     * 按 gatewayId 查询最新的一条内容（用于情报板监控卡片）
     */
    @Select("SELECT * FROM t_content_monitor WHERE gateway_id = #{gatewayId} ORDER BY create_time DESC LIMIT 1")
    ContentMonitor selectLatestByGatewayId(@Param("gatewayId") String gatewayId);

    /**
     * 按情报板 IP（source_ip）查询最新的一条内容
     * 发布网关将 UDP 包转发给情报板，source_ip 就是情报板目标 IP
     */
    @Select("SELECT * FROM t_content_monitor WHERE source_ip = #{boardIp} ORDER BY receive_time DESC LIMIT 1")
    ContentMonitor selectLatestByBoardIp(@Param("boardIp") String boardIp);

    /**
     * 按 chainId 查询最近一条内容记录
     */
    @Select("SELECT * FROM t_content_monitor WHERE chain_id = #{chainId} ORDER BY receive_time DESC LIMIT 1")
    ContentMonitor selectLatestByChainId(@Param("chainId") Long chainId);

    @Select("SELECT * FROM t_content_monitor WHERE play_batch_id = #{playBatchId} " +
            "ORDER BY play_batch_seq ASC, receive_time ASC, id ASC")
    List<ContentMonitor> selectBatchByPlayBatchId(@Param("playBatchId") String playBatchId);

    /**
     * 按情报板 board_ip + board_port 查询最新一条内容
     */
    @Select("SELECT * FROM t_content_monitor WHERE board_ip = #{boardIp} AND board_port = #{boardPort} " +
            "ORDER BY receive_time DESC, id DESC LIMIT 1")
    ContentMonitor selectLatestByBoard(@Param("boardIp") String boardIp, @Param("boardPort") Integer boardPort);

    /**
     * 查询指定情报板最近一次已完整接收的播放批次。
     */
    @Select("SELECT content.* FROM t_content_monitor content " +
            "INNER JOIN (" +
            "SELECT play_batch_id, MAX(receive_time) AS batch_receive_time, MAX(id) AS batch_max_id " +
            "FROM t_content_monitor " +
            "WHERE board_ip = #{boardIp} AND board_port = #{boardPort} " +
            "AND play_batch_id IS NOT NULL AND play_batch_id <> '' " +
            "AND play_batch_seq IS NOT NULL AND play_batch_size > 0 " +
            "GROUP BY play_batch_id " +
            "HAVING COUNT(DISTINCT play_batch_seq) >= MAX(play_batch_size) " +
            "ORDER BY batch_receive_time DESC, batch_max_id DESC LIMIT 1" +
            ") latest_batch ON latest_batch.play_batch_id = content.play_batch_id " +
            "WHERE content.board_ip = #{boardIp} AND content.board_port = #{boardPort} " +
            "ORDER BY content.play_batch_seq ASC, content.receive_time ASC, content.id ASC")
    List<ContentMonitor> selectLatestCompleteBatchByBoard(@Param("boardIp") String boardIp,
                                                          @Param("boardPort") Integer boardPort);

    /**
     * 查询所有不重复的 gateway_id（用于聚合接口遍历）
     */
    @Select("SELECT DISTINCT gateway_id FROM t_content_monitor WHERE gateway_id IS NOT NULL")
    List<String> selectDistinctGatewayIds();

    /**
     * 统计今日检测总数（有识别时间的记录）
     */
    @Select("SELECT COUNT(*) FROM t_content_monitor WHERE DATE(recognition_time) = CURDATE()")
    Long countToday();

    /**
     * 统计今日违规数
     */
    @Select("SELECT COUNT(*) FROM t_content_monitor WHERE DATE(recognition_time) = CURDATE() AND is_violation = 1")
    Long countViolationToday();

    /**
     * 统计今日合规数
     */
    @Select("SELECT COUNT(*) FROM t_content_monitor WHERE DATE(recognition_time) = CURDATE() AND is_violation = 0 AND status != 'pending'")
    Long countCompliantToday();

    /**
     * 查询指定情报板的所有内容记录的文件名列表（用于 Redis 缓存清理）
     * 同时从 file_name 和 description 字段提取文件名，覆盖缓存存入中/传输中等 is_violation 为 NULL 的记录
     * alarm 模块据此扫描删除 publish-gateway ContentCache
     */
    @Select("SELECT DISTINCT file_name FROM t_content_monitor " +
            "WHERE board_ip = #{boardIp} AND board_port = #{boardPort} AND file_name IS NOT NULL " +
            "UNION " +
            "SELECT DISTINCT SUBSTRING_INDEX(SUBSTRING_INDEX(description, '\\\\', -1), ' ', 1) " +
            "FROM t_content_monitor " +
            "WHERE board_ip = #{boardIp} AND board_port = #{boardPort} AND description LIKE '%\\\\%'")
    List<String> selectViolationFileNamesByBoard(@Param("boardIp") String boardIp, @Param("boardPort") Integer boardPort);

    /**
     * 删除指定情报板的所有内容记录（恢复大屏播放时调用）
     * 清除 board-monitor-list 中该情报板的全部内容展示，为写入默认底图腾位
     */
    @Delete("DELETE FROM t_content_monitor WHERE board_ip = #{boardIp} AND board_port = #{boardPort}")
    int deleteViolationByBoard(@Param("boardIp") String boardIp, @Param("boardPort") Integer boardPort);
}
