package com.gateway.device.core.config.colorlight;

import com.gateway.device.core.config.DiscoveryProperties;
import com.gateway.device.core.config.FontsProperties;
import com.gateway.device.protocol.adapter.colorlight.standard.*;
import com.gateway.device.protocol.api.DeviceAuthStore;
import com.gateway.device.protocol.base.colorlight.standard.ColorLightAccount;
import com.gateway.device.protocol.base.colorlight.standard.discovery.ColorLightDiscoveryProvider;
import com.gateway.device.protocol.base.colorlight.standard.discovery.ColorLightSubnetProbe;
import com.gateway.device.protocol.common.GatewayTimeoutConstants;
import com.gateway.device.protocol.common.constant.DeviceVendor;
import com.gateway.device.protocol.common.constant.VendorDefaultPort;
import com.gateway.device.protocol.model.discovery.DeviceVendorMapping;
import com.gateway.device.transport.codec.colorlight.standard.ColorLightHttpCodec;
import com.gateway.device.transport.netty.NettyTransportManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ColorLight HTTP 协议适配器 Spring Bean 装配。
 */
@Slf4j
@Configuration
public class ColorLightAdapterConfig {

    @Bean
    public ColorLightCredentialStore colorLightCredentialStore(ColorLightProperties props) {
        List<ColorLightAccount> accounts = props.toColorLightAccounts();
        log.info("ColorLight credential store initialized with {} preconfigured account(s)", accounts.size());
        return new ColorLightCredentialStore(accounts);
    }

    @Bean
    public ColorLightHttpCodec colorLightHttpCodec() {
        return new ColorLightHttpCodec();
    }

    @Bean
    public ColorLightSubnetProbe colorLightSubnetProbe(
            DiscoveryProperties discoveryProperties,
            NettyTransportManager transportManager) {
        // 收集所有 COLOR_LIGHT_STANDARD 非 SDK 映射，合并 subnets/port/timeout 配置
        List<DeviceVendorMapping> clMappings = discoveryProperties.getMappings().stream()
                .filter(m -> m.getVendor() == DeviceVendor.COLOR_LIGHT_STANDARD && !m.isSdk())
                .collect(Collectors.toList());

        List<String> subnets = clMappings.stream()
                .map(DeviceVendorMapping::effectiveSubnets)
                .filter(s -> !s.isEmpty())
                .flatMap(List::stream)
                .distinct()
                .collect(Collectors.toList());

        int port = clMappings.stream()
                .filter(m -> m.getPort() > 0)
                .findFirst()
                .map(DeviceVendorMapping::effectivePort)
                .orElse(VendorDefaultPort.COLOR_LIGHT_STANDARD.getPort());

        int timeoutMs = clMappings.stream()
                .filter(m -> m.getTimeoutMs() != GatewayTimeoutConstants.DISCOVERY_DEFAULT_TIMEOUT_MS)
                .findFirst()
                .map(DeviceVendorMapping::getTimeoutMs)
                .orElse(GatewayTimeoutConstants.DISCOVERY_DEFAULT_TIMEOUT_MS);

        int concurrency = clMappings.stream()
                .filter(m -> m.getConcurrency() != GatewayTimeoutConstants.DISCOVERY_DEFAULT_TIMEOUT_MS)
                .findFirst()
                .map(DeviceVendorMapping::getConcurrency)
                .orElse(GatewayTimeoutConstants.DISCOVERY_DEFAULT_TIMEOUT_MS);

        log.info("ColorLight subnet probe: subnets={} port={} timeout={}ms concurrency={}",
                subnets, port, timeoutMs, concurrency);
        return new ColorLightSubnetProbe(
                subnets,
                Collections.singletonList(port),
                timeoutMs,
                concurrency,
                transportManager);
    }

    @Bean
    public ColorLightAdapter colorLightAdapter(
            NettyTransportManager transportManager,
            ColorLightCredentialStore credentialStore,
            ColorLightHttpCodec codec,
            DeviceAuthStore deviceAuthManager,
            FontsProperties fontsProperties) {
        return new ColorLightAdapter(transportManager, credentialStore, codec, deviceAuthManager,
                fontsProperties.getLocalPath());
    }

    @Bean
    public ColorLightDiscoveryProvider colorLightDiscoveryProvider(ColorLightSubnetProbe probe) {
        return new ColorLightDiscoveryProvider(probe);
    }

    @Bean
    public ColorLightDeviceInfoEnricher colorLightDeviceInfoEnricher() {
        return new ColorLightDeviceInfoEnricher();
    }

    @Bean
    public ColorLightDeviceRegistrationProvider colorLightDeviceRegistrationProvider(
            NettyTransportManager transportManager,
            ColorLightCredentialStore credentialStore,
            ColorLightHttpCodec codec) {
        return new ColorLightDeviceRegistrationProvider(transportManager, credentialStore, codec);
    }

    @Bean
    public ColorLightComplianceValidator colorLightComplianceValidator() {
        return new ColorLightComplianceValidator();
    }
}
