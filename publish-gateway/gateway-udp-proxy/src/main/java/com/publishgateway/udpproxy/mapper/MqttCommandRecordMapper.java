package com.publishgateway.udpproxy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.publishgateway.udpproxy.entity.MqttCommandRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface MqttCommandRecordMapper extends BaseMapper<MqttCommandRecord> {

    @Select("SELECT * FROM mqtt_command_record WHERE message_id = #{messageId} LIMIT 1")
    MqttCommandRecord selectByMessageId(@Param("messageId") String messageId);

    @Delete("DELETE FROM mqtt_command_record WHERE expire_at < #{now}")
    int deleteExpiredRecords(@Param("now") LocalDateTime now);
}
