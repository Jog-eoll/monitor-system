package com.gateway.device.core.config;

import com.gateway.device.core.config.colorlight.ColorLightProperties;
import com.gateway.device.core.config.jetfileii.JetFileIIFileExtensionProperties;
import com.gateway.device.core.config.jetfileii.JetFileIIProperties;
import com.gateway.device.core.config.jetfileii.JetFileIITextProperties;
import com.gateway.device.core.config.novastar.NovaViplexCoreProperties;
import com.gateway.device.core.event.listener.DeviceSubmitEventListener;
import com.gateway.device.core.executor.DeviceCommandExecutor;
import com.gateway.device.core.executor.DeviceCommandFactory;
import com.gateway.device.core.router.ProtocolRouter;
import com.gateway.device.core.selector.DeviceSelectorResolver;
import com.gateway.device.core.service.*;
import com.gateway.device.core.store.DeviceAuthManager;
import com.gateway.device.core.store.DeviceRegistryManager;
import com.gateway.device.core.task.InMemoryBatchTaskManager;
import com.gateway.device.core.task.InMemoryIpRegistrationTaskManager;
import com.gateway.device.core.task.TaskHousekeeper;
import com.gateway.device.protocol.adapter.jetfileii.standard.JetFileIIComplianceValidator;
import com.gateway.device.protocol.adapter.jetfileii.standard.JetFileIIDeviceInfoEnricher;
import com.gateway.device.protocol.adapter.jetfileii.standard.JetFileIIDiscoveryProvider;
import com.gateway.device.protocol.adapter.novastar.standard.NovaStandardDeviceInfoEnricher;
import com.gateway.device.protocol.adapter.novastar.standard.NovaStandardDiscoveryProvider;
import com.gateway.device.protocol.api.*;
import com.gateway.device.protocol.base.jetfileii.standard.helper.JetFileIIMessaging;
import com.gateway.device.transport.netty.ConnectionHealthChecker;
import com.gateway.device.transport.netty.NettyTransportConfig;
import com.gateway.device.transport.netty.NettyTransportManager;
import com.gateway.device.transport.netty.http.HttpChannelPool;
import com.gateway.device.transport.netty.tcp.TcpChannelPool;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * gateway-device-core 自动配置。
 */
@Configuration
@ComponentScan(basePackages = "com.gateway.device.core")
@EnableScheduling
@EnableConfigurationProperties({DiscoveryProperties.class, JetFileIITextProperties.class, FontsProperties.class, JetFileIIFileExtensionProperties.class, JetFileIIProperties.class, NovaViplexCoreProperties.class, ColorLightProperties.class})
@Slf4j
public class DeviceCoreAutoConfiguration implements DisposableBean {

    private NettyTransportManager transportManager;

    // ════════════════════════════════════════════════════
    // 传输层
    // ════════════════════════════════════════════════════

    @Bean
    @ConditionalOnMissingBean
    public NettyTransportConfig nettyTransportConfig() {
        return new NettyTransportConfig();
    }

    @Bean
    @ConditionalOnMissingBean
    public TcpChannelPool tcpChannelPool() {
        return new TcpChannelPool();
    }

    @Bean
    @ConditionalOnMissingBean
    public HttpChannelPool httpChannelPool() {
        return new HttpChannelPool();
    }

    @Bean
    @ConditionalOnMissingBean
    public ConnectionHealthChecker connectionHealthChecker(NettyTransportConfig config) {
        return new ConnectionHealthChecker(config);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public NettyTransportManager nettyTransportManager(NettyTransportConfig config,
                                                       TcpChannelPool tcpChannelPool,
                                                       HttpChannelPool httpChannelPool,
                                                       ConnectionHealthChecker healthChecker) {
        this.transportManager = new NettyTransportManager(config, tcpChannelPool, httpChannelPool, healthChecker);
        return this.transportManager;
    }

    @Bean
    public ApplicationRunner nettyTransportManagerStarter(NettyTransportManager transportManager,
                                                          NettyTransportConfig config) {
        return args -> {
            if (!config.isUdpEnabled()) {
                log.info("Netty device UDP transport is disabled");
                return;
            }
            transportManager.startUdp(config.getUdpLocalPort());
        };
    }

    // ════════════════════════════════════════════════════
    // 注册与路由
    // ════════════════════════════════════════════════════

    @Bean
    @ConditionalOnMissingBean
    public DeviceRegistryManager deviceRegistryManager() {
        return new DeviceRegistryManager();
    }

    @Bean
    @ConditionalOnMissingBean
    public DeviceAuthManager deviceAuthManager() {
        return new DeviceAuthManager();
    }

    @Bean
    @ConditionalOnMissingBean
    public DeviceSelectorResolver deviceSelectorResolver(DeviceRegistryManager registry) {
        return new DeviceSelectorResolver(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProtocolRouter protocolRouter(List<VendorProtocolAdapter> adapters) {
        return new ProtocolRouter(adapters);
    }

    // ════════════════════════════════════════════════════
    // 任务管理
    // ════════════════════════════════════════════════════

    @Bean
    @ConditionalOnMissingBean
    public InMemoryBatchTaskManager inMemoryBatchTaskManager() {
        return new InMemoryBatchTaskManager();
    }

    @Bean
    @ConditionalOnMissingBean
    public InMemoryIpRegistrationTaskManager inMemoryIpRegistrationTaskManager() {
        return new InMemoryIpRegistrationTaskManager();
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskHousekeeper taskHousekeeper(InMemoryBatchTaskManager taskManager,
                                           InMemoryIpRegistrationTaskManager ipTaskManager) {
        return new TaskHousekeeper(taskManager, ipTaskManager);
    }

    // ════════════════════════════════════════════════════
    // 执行器
    // ════════════════════════════════════════════════════

    @Bean
    @ConditionalOnMissingBean
    public DeviceCommandFactory deviceCommandFactory() {
        return new DeviceCommandFactory();
    }

    @Bean
    @ConditionalOnMissingBean
    public DeviceCommandExecutor deviceCommandExecutor(
            @Qualifier("deviceTaskPool") ThreadPoolTaskExecutor threadPool,
            ProtocolRouter protocolRouter,
            InMemoryBatchTaskManager taskManager,
            ApplicationEventPublisher eventPublisher) {
        return new DeviceCommandExecutor(threadPool, protocolRouter, taskManager, eventPublisher);
    }

    // ════════════════════════════════════════════════════
    // 服务层
    // ════════════════════════════════════════════════════

    @Bean
    @ConditionalOnMissingBean
    public BatchCommandService batchCommandService(
            InMemoryBatchTaskManager taskManager,
            ApplicationEventPublisher eventPublisher) {
        return new BatchCommandService(taskManager, eventPublisher);
    }

    @Bean
    @ConditionalOnMissingBean
    public IpRegistrationService ipRegistrationService(
            InMemoryIpRegistrationTaskManager taskManager,
            ApplicationEventPublisher eventPublisher) {
        return new IpRegistrationService(taskManager, eventPublisher);
    }

    @Bean
    @ConditionalOnMissingBean
    public DeviceLogoutService deviceLogoutService(
            ApplicationEventPublisher eventPublisher) {
        return new DeviceLogoutService(eventPublisher);
    }

    @Bean
    @ConditionalOnMissingBean
    public DeviceSubmitEventListener deviceSubmitEventListener(
            DeviceSelectorResolver selectorResolver,
            InMemoryBatchTaskManager batchTaskManager,
            DeviceCommandFactory commandFactory,
            DeviceCommandExecutor commandExecutor,
            ApplicationEventPublisher eventPublisher,
            AutoDiscoveryService autoDiscoveryService,
            DeviceRegistryManager deviceRegistry,
            InMemoryIpRegistrationTaskManager ipTaskManager,
            ProtocolRouter router,
            List<DeviceRegistrationProvider> regProviders,
            @Qualifier("deviceTaskPool") ThreadPoolTaskExecutor executor) {
        return new DeviceSubmitEventListener(
                selectorResolver, batchTaskManager, commandFactory, commandExecutor,
                eventPublisher, autoDiscoveryService, deviceRegistry,
                ipTaskManager, router, regProviders, executor);
    }

    @Bean
    @ConditionalOnMissingBean
    public DeviceManagementService deviceManagementService(DeviceRegistryManager registry) {
        return new DeviceManagementService(registry);
    }

    // ════════════════════════════════════════════════════
    // 自动发现
    // ════════════════════════════════════════════════════

    @Bean
    @ConditionalOnMissingBean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("discovery-");
        scheduler.initialize();
        return scheduler;
    }

    @Bean
    @ConditionalOnMissingBean
    public JetFileIIComplianceValidator jetFileIIComplianceValidator() {
        return new JetFileIIComplianceValidator();
    }

    @Bean
    @ConditionalOnMissingBean
    public JetFileIIDiscoveryProvider jetFileIIDiscoveryProvider() {
        return new JetFileIIDiscoveryProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public JetFileIIDeviceInfoEnricher jetFileIIDeviceInfoEnricher(NettyTransportManager transportManager) {
        return new JetFileIIDeviceInfoEnricher(new JetFileIIMessaging(), transportManager);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "device.novastar.standard.discovery.enabled", havingValue = "true")
    public NovaStandardDiscoveryProvider novaStandardDiscoveryProvider() {
        return new NovaStandardDiscoveryProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "device.novastar.standard.discovery.enabled", havingValue = "true")
    public NovaStandardDeviceInfoEnricher novaStandardDeviceInfoEnricher() {
        return new NovaStandardDeviceInfoEnricher();
    }

    @Bean
    @ConditionalOnMissingBean
    public AutoDiscoveryService autoDiscoveryService(
            DiscoveryProperties properties,
            DeviceRegistryManager deviceRegistry,
            ProtocolRouter router,
            TaskScheduler taskScheduler,
            List<DeviceComplianceValidator> validators,
            List<DeviceDiscoveryProvider> discoveryProviders,
            List<DeviceInfoEnricher> enrichers,
            List<DeviceRegistrationProvider> regProviders,
            NettyTransportManager transportManager,
            ApplicationEventPublisher eventPublisher,
            DeviceCommandExecutor commandExecutor,
            DeviceAuthManager deviceAuthManager) {
        return new AutoDiscoveryService(properties,
                deviceRegistry, router, taskScheduler, validators,
                discoveryProviders, enrichers, regProviders,
                transportManager, eventPublisher, commandExecutor,
                deviceAuthManager);
    }

    @Override
    public void destroy() {
        if (transportManager != null) {
            transportManager.close();
        }
    }
}
