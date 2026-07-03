package com.monitorplatform.content.service.discovery;

import com.monitorplatform.content.entity.discovery.DiscoveredService;
import lombok.extern.slf4j.Slf4j;

import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * JmDNS 服务发现监听器
 * 监听局域网内服务的加入、解析和移除事件
 */
@Slf4j
public class JmDNSServiceListener implements ServiceListener {

    /** 已发现的服务列表（线程安全） */
    private final List<DiscoveredService> discoveredServices = new CopyOnWriteArrayList<>();

    /** 服务新增回调 */
    private Consumer<DiscoveredService> onServiceAdded;

    /** 服务移除回调 */
    private Consumer<DiscoveredService> onServiceRemoved;

    /** 服务更新回调 */
    private Consumer<DiscoveredService> onServiceUpdated;

    public JmDNSServiceListener() {
    }

    /**
     * 注册服务新增回调
     */
    public JmDNSServiceListener onServiceAdded(Consumer<DiscoveredService> callback) {
        this.onServiceAdded = callback;
        return this;
    }

    /**
     * 注册服务移除回调
     */
    public JmDNSServiceListener onServiceRemoved(Consumer<DiscoveredService> callback) {
        this.onServiceRemoved = callback;
        return this;
    }

    /**
     * 注册服务更新回调
     */
    public JmDNSServiceListener onServiceUpdated(Consumer<DiscoveredService> callback) {
        this.onServiceUpdated = callback;
        return this;
    }

    @Override
    public void serviceAdded(ServiceEvent event) {
        log.info("[JmDNS] 发现新服务: type={}, name={}", event.getType(), event.getName());
        // 请求解析服务详细信息
        event.getDNS().requestServiceInfo(event.getType(), event.getName(), true);
    }

    @Override
    public void serviceResolved(ServiceEvent event) {
        log.info("[JmDNS] 服务解析完成: type={}, name={}, info={}",
                event.getType(), event.getName(), event.getInfo().getNiceTextString());

        DiscoveredService service = DiscoveredService.from(event.getInfo());
        discoveredServices.add(service);

        if (onServiceAdded != null) {
            try {
                onServiceAdded.accept(service);
            } catch (Exception e) {
                log.error("[JmDNS] 服务新增回调执行失败", e);
            }
        }
    }

    @Override
    public void serviceRemoved(ServiceEvent event) {
        log.info("[JmDNS] 服务离线: type={}, name={}", event.getType(), event.getName());

        String key = event.getName() + "@" + event.getType();
        DiscoveredService removed = null;
        for (DiscoveredService ds : discoveredServices) {
            if (key.equals(ds.getKey())) {
                ds.setOnline(false);
                removed = ds;
                discoveredServices.remove(ds);
                break;
            }
        }

        if (removed != null && onServiceRemoved != null) {
            try {
                onServiceRemoved.accept(removed);
            } catch (Exception e) {
                log.error("[JmDNS] 服务移除回调执行失败", e);
            }
        }
    }

    /**
     * 获取所有已发现的服务
     */
    public List<DiscoveredService> getDiscoveredServices() {
        return discoveredServices;
    }

    /**
     * 按服务类型过滤
     */
    public List<DiscoveredService> getServicesByType(String type) {
        List<DiscoveredService> result = new java.util.ArrayList<>();
        for (DiscoveredService ds : discoveredServices) {
            if (ds.getType() != null && ds.getType().equals(type)) {
                result.add(ds);
            }
        }
        return result;
    }

    /**
     * 清空已发现的服务
     */
    public void clear() {
        discoveredServices.clear();
    }
}
