# JmDNS 服务提供者

## 功能说明

可复用库模块。其他微服务引入此依赖后，注入 `JmDNSServiceProvider` Bean，自行构造 props 即可将本服务注册到局域网 mDNS。

消费者模块（如 `monitor-platform-content`）通过 `_http._tcp.local.` 发现注册的服务，无需预先配置 IP 地址。

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.monitorplatform</groupId>
    <artifactId>monitor-platform-provider</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 注入并注册

```java
import com.monitorplatform.provider.service.JmDNSServiceProvider;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Component
public class ServiceRegistration {

    @Resource
    private JmDNSServiceProvider jmdnsProvider;

    @PostConstruct
    public void registerToMdns() {
        Map<String, String> props = new HashMap<>();
        props.put("description", "设备管理服务");
        props.put("path", "/device/health");
        props.put("version", "1.0.0");

        jmdnsProvider.register("monitor-device", 8062, props);
    }
}
```

### 3. 配置开关（可选）

```yaml
provider:
  jmdns:
    enabled: true   # 默认 true，设为 false 可禁用
```

## API 参考

### JmDNSServiceProvider

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| `register(serviceName, port, props)` | 名称、端口、属性Map | `boolean` | 注册到 mDNS，重复调用会先反注册旧的再注册新的 |
| `deregister()` | — | `boolean` | 取消注册（优雅下线） |
| `updateProperties(serviceName, port, newProps)` | 名称、端口、新属性Map | `boolean` | 更新已注册服务的 TXT 属性 |
| `isRegistered()` | — | `boolean` | 当前是否已注册 |
| `getServiceType()` | — | `String` | 固定返回 `_http._tcp.local.` |

### Props 约定字段

Props 由调用方自行构造，以下为建议字段（非强制）：

| 字段 | 说明 | 示例 |
|------|------|------|
| `description` | 服务描述 | `"设备管理服务"` |
| `path` | 健康检查路径 | `"/device/health"` |
| `version` | 服务版本 | `"1.0.0"` |
| `host` | **自动注入** | 调用方无需填写，框架自动设置本机 IP |

## 配置说明

| 属性 | 说明 | 默认值 |
|------|------|--------|
| `provider.jmdns.enabled` | 是否启用 JmDNS 注册 | `true` |

> 不配置时默认启用。若服务运行环境不支持 mDNS 多播，请设为 `false`。

## 架构说明

```
┌─────────────────────────────────┐      ┌─────────────────────────────────┐
│   服务 A (如 monitor-device)     │      │   服务 B (如 monitor-content)    │
│                                   │      │                                   │
│  ├─引入 monitor-platform-provider │      │  ├─内置 JmDNSServiceConsumer      │
│  ├─注入 JmDNSServiceProvider      │      │  ├─监听 _http._tcp.local.         │
│  └─register("monitor-device",...) │      │  └─discover → 发现服务 A          │
└──────────────┬──────────────────┘      └──────────────┬──────────────────┘
               │ mDNS 多播: _http._tcp.local.            │
               └──────────────────┬──────────────────────┘
                                  │
                          局域网 (224.0.0.251:5353)
```

## 完整示例

以 `monitor-platform-device` 模块为例，如何引入并使用：

**pom.xml**:
```xml
<dependency>
    <groupId>com.monitorplatform</groupId>
    <artifactId>monitor-platform-provider</artifactId>
    <version>1.0.0</version>
</dependency>
```

**注册组件** (新建或追加到已有启动监听):
```java
@Component
public class DeviceServiceRegistration {

    private static final String SERVICE_NAME = "monitor-device";
    private static final int SERVICE_PORT = 8062;

    @Resource
    private JmDNSServiceProvider jmdnsProvider;

    @Value("${server.port:8062}")
    private int serverPort;

    @PostConstruct
    public void init() {
        Map<String, String> props = new HashMap<>();
        props.put("description", "设备管理微服务");
        props.put("path", "/device/health");
        props.put("version", "1.0.0");

        boolean ok = jmdnsProvider.register(SERVICE_NAME, serverPort, props);
        if (ok) {
            log.info("设备服务已注册到局域网 mDNS");
        }
    }
}
```

此后 `monitor-platform-content` 中的 `JmDNSServiceConsumer` 即可自动发现 `monitor-device` 服务。
