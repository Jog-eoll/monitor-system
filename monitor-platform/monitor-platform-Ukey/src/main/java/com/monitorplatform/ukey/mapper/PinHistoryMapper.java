package com.monitorplatform.ukey.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.monitorplatform.ukey.entity.PinHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * PIN码历史记录 Mapper
 */
@Mapper
public interface PinHistoryMapper extends BaseMapper<PinHistory> {

    /**
     * 查询指定证书的最近N条历史密码
     */
    @Select("SELECT * FROM pin_history WHERE cert_serial_no = #{certSerialNo} ORDER BY create_time DESC LIMIT #{limit}")
    List<PinHistory> findRecentByCertSerialNo(@Param("certSerialNo") String certSerialNo, @Param("limit") int limit);

    /**
     * 删除超出保留数量的历史记录
     */
    @Select("DELETE FROM pin_history WHERE cert_serial_no = #{certSerialNo} AND id NOT IN " +
            "(SELECT id FROM (SELECT id FROM pin_history WHERE cert_serial_no = #{certSerialNo} " +
            "ORDER BY create_time DESC LIMIT #{keepCount}) t)")
    void cleanOldHistories(@Param("certSerialNo") String certSerialNo, @Param("keepCount") int keepCount);
}
