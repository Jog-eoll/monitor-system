package com.monitorplatform.content.controller;

import com.monitorplatform.common.entity.Result;
import com.monitorplatform.content.entity.discovery.DiscoveredService;
import com.monitorplatform.content.service.discovery.JmDNSServiceConsumer;
import com.monitorplatform.content.service.discovery.MdnsDeviceRegisterService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JmDNS 服务发现 REST 接口
 * 提供局域网内 mDNS 服务查询能力
 */
@Api(value = "/discovery", tags = {"JmDNS 服务发现 REST 接口 提供局域网内 mDNS 服务查询能力"})
@Slf4j
@RestController
@RequestMapping("/discovery")
public class JmDNSDiscoveryController {

    @Resource
    private JmDNSServiceConsumer jmdnsConsumer;

    @Resource
    private MdnsDeviceRegisterService mdnsDeviceRegisterService;

    /**
     * 获取状态信息
     */
    @ApiOperation(value = "获取状态信息", notes = "获取状态信息", httpMethod = "GET")
    @GetMapping("/status")
    public Result<?> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("running", jmdnsConsumer.isRunning());
        status.put("serviceCount", jmdnsConsumer.getServiceCount());
        status.put("listenerCount", jmdnsConsumer.getListenerCount());
        return Result.success(status);
    }

    /**
     * 获取所有已发现的在线服务
     */
    @ApiOperation(value = "获取所有已发现的在线服务", notes = "获取所有已发现的在线服务", httpMethod = "GET")
    @GetMapping("/services")
    public Result<?> getAllServices() {
        List<DiscoveredService> services = jmdnsConsumer.getAllServices();
        return Result.success(services);
    }

    /**
     * 按名称关键字搜索服务
     */
    @ApiImplicitParams({
            @ApiImplicitParam(paramType = "query", dataType = "string", name = "keyword", value = "", required = true)
    })
    @ApiOperation(value = "按名称关键字搜索服务", notes = "按名称关键字搜索服务", httpMethod = "GET")
    @GetMapping("/services/search")
    public Result<?> searchServices(@RequestParam("keyword") String keyword) {
        List<DiscoveredService> services = jmdnsConsumer.getServicesByNameKeyword(keyword);
        return Result.success(services);
    }

    /**
     * 按主机地址查询服务
     */
    @ApiImplicitParams({
            @ApiImplicitParam(paramType = "query", dataType = "string", name = "host", value = "", required = true)
    })
    @ApiOperation(value = "按主机地址查询服务", notes = "按主机地址查询服务", httpMethod = "GET")
    @GetMapping("/services/host")
    public Result<?> getServiceByHost(@RequestParam("host") String host) {
        DiscoveredService service = jmdnsConsumer.getServiceByHost(host);
        if (service == null) {
            return Result.error("未找到对应主机的服务: " + host);
        }
        return Result.success(service);
    }

    /**
     * 获取当前监听的服务类型
     */
    @ApiOperation(value = "获取当前监听的服务类型", notes = "获取当前监听的服务类型", httpMethod = "GET")
    @GetMapping("/service-type")
    public Result<?> getServiceType() {
        return Result.success(jmdnsConsumer.getServiceType());
    }

    /**
     * 获取网关服务列表（按配置的关键字过滤）
     */
    @ApiOperation(value = "获取网关服务列表（按配置的关键字过滤）", notes = "获取网关服务列表（按配置的关键字过滤）", httpMethod = "GET")
    @GetMapping("/gateways")
    public Result<?> getGatewayServices() {
        List<DiscoveredService> services = jmdnsConsumer.getGatewayServices();
        return Result.success(services);
    }

    /**
     * 手动触发主动扫描指定服务类型
     */
    @ApiOperation(value = "手动触发主动扫描指定服务类型", notes = "手动触发主动扫描指定服务类型", httpMethod = "POST")
    @PostMapping("/scan")
    public Result<?> scanServices() {
        log.info("手动触发 JmDNS 扫描");
        List<DiscoveredService> services = jmdnsConsumer.scanServices();
        return Result.success(services);
    }

    /**
     * Scan mDNS services and register supported devices into unified_device_info.
     */
    @ApiOperation(value = "Scan and register mDNS services", notes = "Scan supported mDNS services and register them as unified devices", httpMethod = "POST")
    @PostMapping("/register-scanned")
    public Result<?> registerScannedServices() {
        log.info("Manual trigger JmDNS scan and device register");
        return Result.success(mdnsDeviceRegisterService.registerScannedDevices());
    }

    /**
     * 重新启动服务发现（重新注册监听）
     */
    @ApiOperation(value = "重新启动服务发现（重新注册监听）", notes = "重新启动服务发现（重新注册监听）", httpMethod = "POST")
    @PostMapping("/restart")
    public Result<?> restartDiscovery() {
        log.info("手动重启 JmDNS 服务发现");
        jmdnsConsumer.stopDiscovery();
        jmdnsConsumer.startDiscovery();
        return Result.success("服务发现已重启");
    }

    /**
     * 停止服务发现
     */
    @ApiOperation(value = "停止服务发现", notes = "停止服务发现", httpMethod = "POST")
    @PostMapping("/stop")
    public Result<?> stopDiscovery() {
        log.info("手动停止 JmDNS 服务发现");
        jmdnsConsumer.stopDiscovery();
        return Result.success("服务发现已停止");
    }
}
