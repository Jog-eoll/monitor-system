# JmDNS 自动搜索广播接入说明

## 1. 目标

平台通过 mDNS/DNS-SD 自动搜索局域网内的业务节点，并按 `deviceType` 过滤出平台关心的设备：

| 设备 | service name | deviceType | role | HTTP 管理端口 |
| --- | --- | --- | --- | --- |
| 信息发布客户端 | `info-publish-client` | `publish_server` | `info_publish_client` | `7080/7081` |
| 发布端加密网关 | `gateway-udp-proxy` | `publish_gateway` | `encrypt_gateway` | `8092` |
| 终端解密网关 | `terminal-udp-gateway` | `terminal_encrypt_gateway` | `decrypt_gateway` | `8093` |

mDNS 只负责局域网发现，平台设备台账、心跳、在线状态仍以 `monitor-platform-registry-client` 自动注册链路为准。

## 2. 广播协议

所有接入方统一广播：

```text
service type: _http._tcp.local.
service port: 当前服务 HTTP 管理端口
TXT metadata:
  deviceType=<设备类型>
  role=<业务角色>
  host=<JmDNSServiceProvider 自动注入的广播地址>
  path=<健康/状态接口路径>
  version=<可选>
  manufacturer=<可选>
  model=<可选>
  remark=<可选>
```

禁止广播 `VAUTH_PASSWORD`、证书路径、UKey PIN、私钥、数据库密码等敏感信息。

## 3. 配置

接入服务统一使用：

```yaml
provider:
  jmdns:
    enabled: ${PROVIDER_JMDNS_ENABLED:true}
    bind-address: ${PROVIDER_JMDNS_BIND_ADDRESS:}
    advertise-host: ${PROVIDER_JMDNS_ADVERTISE_HOST:}
```

| 配置 | 说明 | 建议 |
| --- | --- | --- |
| `PROVIDER_JMDNS_ENABLED` | 是否启用 mDNS 广播 | 默认 `true` |
| `PROVIDER_JMDNS_BIND_ADDRESS` | JmDNS 绑定的本机 IPv4 | 生产建议显式写局域网 IP |
| `PROVIDER_JMDNS_ADVERTISE_HOST` | TXT 中对外声明的 `host` | 通常与绑定 IP 一致 |

Docker 使用 `host` 网络时，容器仍能看到宿主机 `docker0/br-*` 网卡。若发现绑定到 `172.*` 网桥地址，应显式配置 `PROVIDER_JMDNS_BIND_ADDRESS=192.168.x.x`。

## 4. 平台搜索

平台 content 服务通过现有接口搜索：

```http
POST /discovery/scan
```

接口契约不变，返回 `data` 列表；其中 `properties` 已结构化为 JSON 对象。平台只保留以下 `deviceType`：

```text
publish_gateway
terminal_encrypt_gateway
publish_server
```

示例：

```json
{
  "type": "_http._tcp.local.",
  "name": "terminal-udp-gateway",
  "hostAddress": "192.168.1.25",
  "port": 8093,
  "properties": {
    "deviceType": "terminal_encrypt_gateway",
    "host": "192.168.1.25",
    "path": "/udp-proxy/rules/running",
    "role": "decrypt_gateway"
  },
  "online": true
}
```

## 5. 验证

1. 启动 `monitor-platform-content`，确认日志出现：

```text
[JmDNS] JmDNS 实例创建成功
```

2. 启动接入方，确认对应日志：

```text
[InfoPublishClient-mDNS] registered
[PublishGateway-mDNS] registered
[TerminalGateway-mDNS] registered
```

3. 调用：

```bash
curl -X POST http://<content-host>:8065/discovery/scan
```

确认返回结果只包含 `publish_server`、`publish_gateway`、`terminal_encrypt_gateway` 三类设备，且 `properties.deviceType` 为结构化字段。

4. 如搜索不到，优先检查：

```bash
ip -4 addr
docker logs <container> --tail=100 | grep mDNS
```

确认绑定地址为局域网 IP，UDP 5353 多播未被防火墙或 Docker 网络隔离。
