package com.monitorplatform.content.service;

import com.monitorplatform.content.entity.ContentMonitor;
import com.monitorplatform.content.mapper.ContentMonitorMapper;
import com.monitorplatform.content.service.impl.ContentMonitorServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContentMonitorCurrentBatchSelectionTest {

    @Test
    void incompleteNewestBatchKeepsPreviousCompleteBatchVisible() {
        ContentMonitor latestPartial = content("new-1", "SPB-new", 1, 2);
        List<ContentMonitor> previousComplete = Arrays.asList(
                content("old-1", "SPB-old", 1, 2),
                content("old-2", "SPB-old", 2, 2));

        ContentMonitorServiceImpl service = new ContentMonitorServiceImpl();
        ReflectionTestUtils.setField(service, "contentMonitorMapper", mapperReturning(previousComplete));

        List<ContentMonitor> selected = ReflectionTestUtils.invokeMethod(
                service, "selectDisplayContents", latestPartial);

        assertEquals(Arrays.asList("old-1", "old-2"), contentIds(selected));
    }

    @Test
    void completeNewestBatchReplacesPreviousDisplayBatch() {
        ContentMonitor latest = content("new-2", "SPB-new", 2, 2);
        List<ContentMonitor> newestComplete = Arrays.asList(
                content("new-1", "SPB-new", 1, 2),
                content("new-2", "SPB-new", 2, 2));

        List<ContentMonitor> selected = select(latest, mapperReturning(newestComplete));

        assertEquals(Arrays.asList("new-1", "new-2"), contentIds(selected));
    }

    @Test
    void firstIncompleteBatchDoesNotExposePartialContents() {
        ContentMonitor latestPartial = content("new-1", "SPB-new", 1, 2);

        List<ContentMonitor> selected = select(
                latestPartial, mapperReturning(Collections.<ContentMonitor>emptyList()));

        assertEquals(Collections.emptyList(), selected);
    }

    @Test
    void recordWithoutBatchMetadataRemainsVisibleForLegacyCompatibility() {
        ContentMonitor legacy = content("legacy-1", null, 0, 0);

        List<ContentMonitor> selected = select(legacy, mapperFailingOnQuery());

        assertEquals(Collections.singletonList("legacy-1"), contentIds(selected));
    }

    @Test
    void differentBoardsReadIndependentCompleteBatches() {
        ContentMonitor boardA = content("a-latest", "SPB-a", 1, 1);
        ContentMonitor boardB = content("b-latest", "SPB-b", 1, 1);
        boardB.setBoardIp("192.168.113.89");

        ContentMonitorMapper mapper = mapperByBoard();

        assertEquals(Collections.singletonList("a-1"), contentIds(select(boardA, mapper)));
        assertEquals(Collections.singletonList("b-1"), contentIds(select(boardB, mapper)));
    }

    private static List<ContentMonitor> select(ContentMonitor latest, ContentMonitorMapper mapper) {
        ContentMonitorServiceImpl service = new ContentMonitorServiceImpl();
        ReflectionTestUtils.setField(service, "contentMonitorMapper", mapper);
        return ReflectionTestUtils.invokeMethod(service, "selectDisplayContents", latest);
    }

    private static ContentMonitorMapper mapperReturning(List<ContentMonitor> completeBatch) {
        return (ContentMonitorMapper) Proxy.newProxyInstance(
                ContentMonitorMapper.class.getClassLoader(),
                new Class[]{ContentMonitorMapper.class},
                (proxy, method, args) -> {
                    if ("selectLatestCompleteBatchByBoard".equals(method.getName())) {
                        assertEquals("192.168.113.88", args[0]);
                        assertEquals(9520, args[1]);
                        return completeBatch;
                    }
                    if ("selectBatchByPlayBatchId".equals(method.getName())) {
                        return Arrays.asList(content("new-1", "SPB-new", 1, 2));
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static ContentMonitorMapper mapperByBoard() {
        return (ContentMonitorMapper) Proxy.newProxyInstance(
                ContentMonitorMapper.class.getClassLoader(),
                new Class[]{ContentMonitorMapper.class},
                (proxy, method, args) -> {
                    if ("selectLatestCompleteBatchByBoard".equals(method.getName())) {
                        String boardIp = (String) args[0];
                        if ("192.168.113.88".equals(boardIp)) {
                            return Collections.singletonList(content("a-1", "SPB-a", 1, 1));
                        }
                        if ("192.168.113.89".equals(boardIp)) {
                            ContentMonitor item = content("b-1", "SPB-b", 1, 1);
                            item.setBoardIp(boardIp);
                            return Collections.singletonList(item);
                        }
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static ContentMonitorMapper mapperFailingOnQuery() {
        return (ContentMonitorMapper) Proxy.newProxyInstance(
                ContentMonitorMapper.class.getClassLoader(),
                new Class[]{ContentMonitorMapper.class},
                (proxy, method, args) -> {
                    if (method.getName().startsWith("select")) {
                        throw new AssertionError("legacy single record must not query a playback batch");
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static ContentMonitor content(String contentId, String batchId, int sequence, int batchSize) {
        ContentMonitor content = new ContentMonitor();
        content.setContentId(contentId);
        content.setBoardIp("192.168.113.88");
        content.setBoardPort(9520);
        content.setPlayBatchId(batchId);
        content.setPlayBatchSeq(sequence);
        content.setPlayBatchSize(batchSize);
        return content;
    }

    private static List<String> contentIds(List<ContentMonitor> contents) {
        java.util.ArrayList<String> ids = new java.util.ArrayList<>();
        for (ContentMonitor content : contents) {
            ids.add(content.getContentId());
        }
        return ids;
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
}
