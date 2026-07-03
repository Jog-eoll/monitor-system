package com.infopublish.client.service.impl;

import com.infopublish.client.config.AppConfig.ProcessBindProperties;
import com.infopublish.client.service.UdpPortOwnerService;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * UDP 端口归属查询服务实现
 *
 * <p>通过 Windows iphlpapi.dll 的 GetExtendedUdpTable API 查询 UDP 端口归属进程。
 */
@Slf4j
@Service
public class UdpPortOwnerServiceImpl implements UdpPortOwnerService {

    private static final int AF_INET = 2;
    private static final int UDP_TABLE_OWNER_PID = 1;
    private static final int NO_ERROR = 0;
    private static final int ERROR_INSUFFICIENT_BUFFER = 122;

    private interface IPHlpAPI extends Library {
        int GetExtendedUdpTable(
                Pointer pUdpTable,
                IntByReference pdwSize,
                boolean bOrder,
                int ulAf,
                int TableClass,
                int Reserved);
    }

    private static volatile IPHlpAPI ipHlpApi;
    private static volatile boolean apiInitialized = false;
    private static volatile boolean apiAvailable = false;

    @Resource
    private ProcessBindProperties properties;

    private volatile List<UdpEntry> cachedEntries = Collections.emptyList();
    private volatile long cacheExpireAt = 0L;

    private static synchronized void ensureInit() {
        if (apiInitialized) return;
        try {
            ipHlpApi = Native.load("iphlpapi", IPHlpAPI.class);
            apiAvailable = true;
            IntByReference testSize = new IntByReference(0);
            ipHlpApi.GetExtendedUdpTable(null, testSize, false, AF_INET, UDP_TABLE_OWNER_PID, 0);
        } catch (UnsatisfiedLinkError e) {
            apiAvailable = false;
        } catch (Exception e) {
            apiAvailable = true;
        } finally {
            apiInitialized = true;
        }
    }

    @Override
    public List<UdpEntry> queryUdpTable() {
        ensureInit();
        if (!apiAvailable) {
            return Collections.emptyList();
        }

        long now = System.currentTimeMillis();
        long cacheMillis = properties != null ? properties.getUdpTableCacheMillis() : 200L;
        if (cacheMillis > 0 && cacheExpireAt > now) {
            return cachedEntries;
        }

        List<UdpEntry> entries = new ArrayList<>();
        try {
            IntByReference size = new IntByReference(0);
            int ret = ipHlpApi.GetExtendedUdpTable(
                    null, size, false, AF_INET, UDP_TABLE_OWNER_PID, 0);
            if (ret != ERROR_INSUFFICIENT_BUFFER && ret != NO_ERROR) {
                log.warn("[UDP查询] GetExtendedUdpTable 获取缓冲区大小失败: ret={}", ret);
                return entries;
            }

            Memory buffer = new Memory(size.getValue());
            ret = ipHlpApi.GetExtendedUdpTable(
                    buffer, size, false, AF_INET, UDP_TABLE_OWNER_PID, 0);
            if (ret != NO_ERROR) {
                log.warn("[UDP查询] GetExtendedUdpTable 获取数据失败: ret={}", ret);
                return entries;
            }

            int numEntries = buffer.getInt(0);
            for (int i = 0; i < numEntries; i++) {
                long offset = 4L + (long) i * 12;
                int rawAddr = buffer.getInt(offset);
                int rawPort = buffer.getInt(offset + 4);
                int pid = buffer.getInt(offset + 8);

                String ip = rawAddrToIp(rawAddr);
                int port = ntohs(rawPort);
                entries.add(new UdpEntry(ip, port, pid));
            }
        } catch (Exception e) {
            log.error("[UDP查询] GetExtendedUdpTable 调用异常: {}", e.getMessage());
        }
        List<UdpEntry> snapshot = Collections.unmodifiableList(entries);
        if (cacheMillis > 0) {
            cachedEntries = snapshot;
            cacheExpireAt = now + cacheMillis;
        }
        return snapshot;
    }

    @Override
    public boolean isAvailable() {
        ensureInit();
        return apiAvailable;
    }

    private String rawAddrToIp(int rawAddr) {
        return (rawAddr & 0xFF) + "." +
                ((rawAddr >> 8) & 0xFF) + "." +
                ((rawAddr >> 16) & 0xFF) + "." +
                ((rawAddr >> 24) & 0xFF);
    }

    private int ntohs(int rawPort) {
        int low16 = rawPort & 0xFFFF;
        return ((low16 & 0xFF) << 8) | ((low16 >> 8) & 0xFF);
    }
}
