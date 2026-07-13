package com.monitorplatform.log.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.monitorplatform.log.dto.PublishAuditLogQueryDTO;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExistingLogQueryMapperContractTest {

    @Test
    void publishAuditSqlAssociatesDiagnosticEventsByPublishRequestId() throws Exception {
        String sql = pagePublishAuditSql();

        assertTrue(sql.contains("d1.trace_id = c.publish_request_id"),
                "sign_event must match diagnostic trace_id to t_content_monitor.publish_request_id");
        assertTrue(sql.contains("d2.trace_id = c.publish_request_id"),
                "verify_event must match diagnostic trace_id to t_content_monitor.publish_request_id");
        assertTrue(sql.contains("d3.trace_id = c.publish_request_id"),
                "gateway_event must match diagnostic trace_id to t_content_monitor.publish_request_id");
        assertTrue(sql.contains("c.publish_request_id"),
                "publish audit SQL must expose the dedicated publish request id field");
        assertFalse(sql.contains("trace_id = c.request_id"),
                "publish audit SQL must not reuse AI detection request_id for publish trace matching");
    }

    @Test
    void publishAuditSqlIncludesQingsongSecureDeliveryDiagnosticEvents() throws Exception {
        String sql = pagePublishAuditSql();

        assertTrue(sql.contains("SECURE_DELIVERY_PLAYLIST_VERIFIED"));
        assertTrue(sql.contains("SECURE_DELIVERY_PLAYLIST_VERIFY_FAILED"));
        assertTrue(sql.contains("SECURE_DELIVERY_PLAYLIST_ITEM_VERIFIED"));
        assertTrue(sql.contains("SECURE_DELIVERY_PLAYLIST_ITEM_VERIFY_FAILED"));
        assertTrue(sql.contains("SECURE_DELIVERY_ACK_SUMMARY"));
        assertTrue(sql.contains("SECURE_PUBLISH_CONTENT_REPORTED"));
        assertTrue(sql.contains("SECURE_PUBLISH_CONTENT_REPORT_FAILED"));
    }

    private static String pagePublishAuditSql() throws Exception {
        Method method = ExistingLogQueryMapper.class.getMethod("pagePublishAudit",
                Page.class, PublishAuditLogQueryDTO.class);
        Select select = method.getAnnotation(Select.class);
        return String.join("\n", select.value());
    }
}
