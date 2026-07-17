package com.infopublish.client.service.impl;

import com.infopublish.client.entity.dto.publish.ContentPublishV2BaseRequest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ContentPublishV2DefaultTimeoutTest {

    public static void main(String[] args) throws Exception {
        ContentPublishV2DefaultTimeoutTest test = new ContentPublishV2DefaultTimeoutTest();
        test.usesConfiguredDefaultWhenRequestDoesNotSpecifyTimeout();
        test.keepsExplicitRequestTimeout();
    }

    public void usesConfiguredDefaultWhenRequestDoesNotSpecifyTimeout() throws Exception {
        ContentPublishV2ServiceImpl service = new ContentPublishV2ServiceImpl();
        setField(service, "defaultDeliveryTimeoutMs", 7200000);

        Integer actual = resolveTimeoutMs(service, new ContentPublishV2BaseRequest());

        if (!Integer.valueOf(7200000).equals(actual)) {
            throw new AssertionError("Expected configured default timeout 7200000 but was " + actual);
        }
    }

    public void keepsExplicitRequestTimeout() throws Exception {
        ContentPublishV2ServiceImpl service = new ContentPublishV2ServiceImpl();
        setField(service, "defaultDeliveryTimeoutMs", 7200000);
        ContentPublishV2BaseRequest request = new ContentPublishV2BaseRequest();
        request.setTimeoutMs(123456);

        Integer actual = resolveTimeoutMs(service, request);

        if (!Integer.valueOf(123456).equals(actual)) {
            throw new AssertionError("Expected explicit timeout 123456 but was " + actual);
        }
    }

    private Integer resolveTimeoutMs(ContentPublishV2ServiceImpl service,
                                     ContentPublishV2BaseRequest request) throws Exception {
        Method method = ContentPublishV2ServiceImpl.class.getDeclaredMethod(
                "resolveTimeoutMs", ContentPublishV2BaseRequest.class);
        method.setAccessible(true);
        return (Integer) method.invoke(service, request);
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
