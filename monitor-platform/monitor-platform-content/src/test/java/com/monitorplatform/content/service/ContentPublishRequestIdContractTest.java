package com.monitorplatform.content.service;

import com.monitorplatform.content.entity.ContentMonitor;
import com.monitorplatform.content.entity.dto.ContentReceiveDTO;
import com.monitorplatform.content.entity.dto.QwenDetectionRequestDTO;
import com.monitorplatform.content.mapper.ContentMonitorMapper;
import com.monitorplatform.content.service.impl.ContentMonitorServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ContentPublishRequestIdContractTest {

    @Test
    void contentEntitiesAndInboundDtosDeclarePublishRequestId() throws Exception {
        assertNotNull(ContentMonitor.class.getDeclaredField("publishRequestId"));
        assertNotNull(QwenDetectionRequestDTO.class.getDeclaredField("publishRequestId"));
        assertNotNull(ContentReceiveDTO.class.getDeclaredField("publishRequestId"));
    }

    @Test
    void detectionPendingRecordPersistsPublishRequestIdWithoutOverwritingAiRequestId() throws Exception {
        AtomicReference<ContentMonitor> inserted = new AtomicReference<>();
        ContentDetectionService service = new ContentDetectionService();
        ReflectionTestUtils.setField(service, "contentMonitorMapper", mapperCapturingInsert(inserted));
        ReflectionTestUtils.setField(service, "operateLogWriter", null);

        QwenDetectionRequestDTO request = new QwenDetectionRequestDTO();
        request.setBusinessId("playlist-157");
        request.setDeviceId("gateway-1");
        request.setDeviceName("gateway-1");
        request.setContentType("image");
        request.setMinioPath("images/demo.bmp");
        setField(request, "publishRequestId", "REQ-PUBLISH-20260707-157");

        Method method = ContentDetectionService.class.getDeclaredMethod(
                "createPendingRecord", QwenDetectionRequestDTO.class, String.class);
        method.setAccessible(true);
        ContentMonitor record = (ContentMonitor) method.invoke(service, request, "image");

        assertEquals("REQ-PUBLISH-20260707-157", readField(record, "publishRequestId"));
        assertEquals("REQ-PUBLISH-20260707-157", readField(inserted.get(), "publishRequestId"));
        assertEquals(null, record.getRequestId(), "AI request_id must remain independent until detection result returns");
    }

    @Test
    void contentReceiveRecordPersistsPublishRequestId() throws Exception {
        AtomicReference<ContentMonitor> inserted = new AtomicReference<>();
        ContentMonitorServiceImpl service = new ContentMonitorServiceImpl();
        ReflectionTestUtils.setField(service, "contentMonitorMapper", mapperCapturingInsert(inserted));
        ReflectionTestUtils.setField(service, "selfProxy", new NoopContentMonitorServiceImpl());
        ReflectionTestUtils.setField(service, "operateLogWriter", null);

        ContentReceiveDTO dto = new ContentReceiveDTO();
        dto.setContentId("playlist-157");
        dto.setGatewayId("gateway-1");
        dto.setContentType("text");
        dto.setData("hello");
        setField(dto, "publishRequestId", "REQ-PUBLISH-20260707-157");

        ContentMonitor record = service.receiveContent(dto);

        assertEquals("REQ-PUBLISH-20260707-157", readField(record, "publishRequestId"));
        assertEquals("REQ-PUBLISH-20260707-157", readField(inserted.get(), "publishRequestId"));
    }

    @Test
    void securePublishPlaylistRecordPersistsPublishRequestIdAndSkipsRecognition() throws Exception {
        AtomicReference<ContentMonitor> inserted = new AtomicReference<>();
        TrackingContentMonitorServiceImpl selfProxy = new TrackingContentMonitorServiceImpl();
        ContentMonitorServiceImpl service = new ContentMonitorServiceImpl();
        ReflectionTestUtils.setField(service, "contentMonitorMapper", mapperCapturingInsert(inserted));
        ReflectionTestUtils.setField(service, "selfProxy", selfProxy);
        ReflectionTestUtils.setField(service, "operateLogWriter", null);

        ContentReceiveDTO dto = new ContentReceiveDTO();
        dto.setContentId("157");
        dto.setGatewayId("SECURE_PUBLISH");
        dto.setContentType("playlist");
        dto.setData("{\"files\":[{\"fileName\":\"demo.bmp\"}]}");
        dto.setBoardIp("192.168.113.88");
        dto.setBoardPort(9520);
        dto.setPlayBatchId("157");
        dto.setPlayBatchSeq(0);
        dto.setPlayBatchSize(1);
        setField(dto, "publishRequestId", "REQ-PUBLISH-20260709-157");

        ContentMonitor record = service.receiveContent(dto);

        assertEquals("157", record.getContentId());
        assertEquals("playlist", record.getContentType());
        assertEquals("normal", record.getStatus());
        assertEquals("157", record.getPlayBatchId());
        assertEquals("REQ-PUBLISH-20260709-157", readField(record, "publishRequestId"));
        assertEquals("REQ-PUBLISH-20260709-157", readField(inserted.get(), "publishRequestId"));
        assertFalse(selfProxy.asyncRecognizeCalled, "playlist records are metadata and must not enter content recognition");
    }

    @Test
    void securePublishPlaylistRecordUpdatesExistingContentIdInsteadOfDuplicating() throws Exception {
        AtomicReference<ContentMonitor> updated = new AtomicReference<>();
        TrackingContentMonitorServiceImpl selfProxy = new TrackingContentMonitorServiceImpl();
        ContentMonitorServiceImpl service = new ContentMonitorServiceImpl();
        ReflectionTestUtils.setField(service, "contentMonitorMapper", mapperUpdatingExistingPlaylist(updated));
        ReflectionTestUtils.setField(service, "selfProxy", selfProxy);
        ReflectionTestUtils.setField(service, "operateLogWriter", null);

        ContentReceiveDTO dto = new ContentReceiveDTO();
        dto.setContentId("157");
        dto.setGatewayId("SECURE_PUBLISH");
        dto.setContentType("playlist");
        dto.setData("{\"files\":[{\"fileName\":\"new.bmp\"}]}");
        dto.setPlayBatchId("157");
        dto.setPlayBatchSize(1);
        setField(dto, "publishRequestId", "REQ-PUBLISH-20260709-158");

        ContentMonitor record = service.receiveContent(dto);

        assertEquals(Long.valueOf(1001L), record.getId());
        assertEquals("REQ-PUBLISH-20260709-158", readField(updated.get(), "publishRequestId"));
        assertEquals("normal", updated.get().getStatus());
        assertFalse(selfProxy.asyncRecognizeCalled, "playlist upsert must not enter content recognition");
    }

    private static ContentMonitorMapper mapperCapturingInsert(AtomicReference<ContentMonitor> inserted) {
        return (ContentMonitorMapper) Proxy.newProxyInstance(
                ContentMonitorMapper.class.getClassLoader(),
                new Class[]{ContentMonitorMapper.class},
                (proxy, method, args) -> {
                    if ("insert".equals(method.getName())) {
                        inserted.set((ContentMonitor) args[0]);
                        return 1;
                    }
                    if ("selectByContentId".equals(method.getName())
                            || "selectLatestByMinioPathAndType".equals(method.getName())) {
                        return null;
                    }
                    if ("updateById".equals(method.getName())) {
                        return 1;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static ContentMonitorMapper mapperUpdatingExistingPlaylist(AtomicReference<ContentMonitor> updated) {
        return (ContentMonitorMapper) Proxy.newProxyInstance(
                ContentMonitorMapper.class.getClassLoader(),
                new Class[]{ContentMonitorMapper.class},
                (proxy, method, args) -> {
                    if ("selectByContentId".equals(method.getName())) {
                        ContentMonitor existing = new ContentMonitor();
                        existing.setId(1001L);
                        existing.setContentId("157");
                        existing.setContentType("playlist");
                        return existing;
                    }
                    if ("updateById".equals(method.getName())) {
                        updated.set((ContentMonitor) args[0]);
                        return 1;
                    }
                    if ("insert".equals(method.getName())) {
                        throw new AssertionError("existing playlist content must be updated, not inserted");
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (boolean.class.equals(type)) {
            return false;
        }
        if (void.class.equals(type)) {
            return null;
        }
        return 0;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static class NoopContentMonitorServiceImpl extends ContentMonitorServiceImpl {
        @Override
        public void asyncRecognize(ContentMonitor record) {
            // no-op for contract test
        }
    }

    private static class TrackingContentMonitorServiceImpl extends ContentMonitorServiceImpl {
        private boolean asyncRecognizeCalled;

        @Override
        public void asyncRecognize(ContentMonitor record) {
            this.asyncRecognizeCalled = true;
        }
    }
}
