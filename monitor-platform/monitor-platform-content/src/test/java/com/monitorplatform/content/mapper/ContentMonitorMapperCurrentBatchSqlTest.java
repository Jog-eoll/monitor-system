package com.monitorplatform.content.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentMonitorMapperCurrentBatchSqlTest {

    @Test
    void latestCompleteBatchQueryIsScopedToBoardAndRequiresEverySequence() throws Exception {
        Method method = ContentMonitorMapper.class.getMethod(
                "selectLatestCompleteBatchByBoard", String.class, Integer.class);
        Select select = method.getAnnotation(Select.class);

        assertNotNull(select, "latest complete batch query must use an explicit SQL contract");
        String sql = String.join(" ", select.value()).toLowerCase(Locale.ROOT);
        assertTrue(sql.contains("board_ip = #{boardip}"));
        assertTrue(sql.contains("board_port = #{boardport}"));
        assertTrue(sql.contains("group by play_batch_id"));
        assertTrue(sql.contains("count(distinct play_batch_seq)"));
        assertTrue(sql.contains("max(play_batch_size)"));
        assertTrue(sql.contains("limit 1"));
    }
}
