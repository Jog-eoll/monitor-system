package com.publishgateway.udpproxy.service.impl;

import com.sun.net.httpserver.HttpServer;
import org.junit.Test;

import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;

public class SecureDeliveryServiceImplLargeDownloadTest {

    @Test
    public void downloadFileAllowsTwoGigabyteLimitWithoutIntegerOverflow() throws Exception {
        final byte[] body = "small-video-probe".getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/video.bin", exchange -> {
            exchange.sendResponseHeaders(200, body.length);
            OutputStream output = exchange.getResponseBody();
            try {
                output.write(body);
            } finally {
                output.close();
            }
        });
        server.start();
        try {
            SecureDeliveryServiceImpl service = new SecureDeliveryServiceImpl();
            setField(service, "downloadTimeoutMs", 30000);
            setField(service, "maxFileSizeMb", 2048);

            Method method = SecureDeliveryServiceImpl.class.getDeclaredMethod("downloadFile", String.class);
            method.setAccessible(true);
            byte[] downloaded = (byte[]) method.invoke(service,
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/video.bin");

            assertNotNull(downloaded);
            assertArrayEquals(body, downloaded);
        } finally {
            server.stop(0);
        }
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
