# 简化版测试脚本 - 测试自动下发功能
# 测试场景：创建链路节点后自动触发配置下发

$PlatformHost = "127.0.0.1"
$PlatformPort = "8067"
$PlatformUrl = "http://${PlatformHost}:${PlatformPort}"

Write-Host "================================================" -ForegroundColor Cyan
Write-Host "测试自动下发功能" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

# 测试：创建链路节点（模拟前端点击"确认"）
Write-Host "[测试] 创建链路节点并自动下发配置..." -ForegroundColor Yellow

# 准备测试数据
$createNodesRequest = @{
    chainId = 1  # 替换为实际的链路ID
    rootNodes = @(
        @{
            deviceId = "PUB-GW-001"
            deviceName = "发布加密网关1"
            deviceType = "publish_gateway"
            deviceIp = "192.168.1.25"
            children = @(
                @{
                    deviceId = "TERM-GW-001"
                    deviceName = "终端加密网关1"
                    deviceType = "terminal_encrypt_gateway"
                    deviceIp = "192.168.2.50"
                    children = @(
                        @{
                            deviceId = "INFO-BOARD-001"
                            deviceName = "情报板1"
                            deviceType = "info_board"
                            deviceIp = "192.168.2.100"
                        }
                    )
                }
            )
        }
    )
    operatorBy = "admin"
} | ConvertTo-Json -Depth 10

try {
    Write-Host "发送请求到: $PlatformUrl/chain/create/nodes" -ForegroundColor Gray
    
    $response = Invoke-RestMethod -Uri "$PlatformUrl/chain/create/nodes" -Method Post -Body $createNodesRequest -ContentType "application/json"
    
    if ($response.code -eq 200) {
        Write-Host "[PASS] 链路节点创建成功，配置已自动下发" -ForegroundColor Green
        Write-Host "      说明: 节点创建成功后会自动触发配置下发到发布网关" -ForegroundColor Gray
        Write-Host ""
        Write-Host "      请查看日志确认自动下发情况:" -ForegroundColor Yellow
        Write-Host "        - 管控平台日志: 搜索 【自动下发】" -ForegroundColor Gray
        Write-Host "        - 发布网关日志: 搜索 【发布网关】收到配置下发" -ForegroundColor Gray
    } else {
        Write-Host "[FAIL] 创建失败: $($response.msg)" -ForegroundColor Red
    }
} catch {
    Write-Host "[FAIL] 请求失败: $_" -ForegroundColor Red
    Write-Host ""
    Write-Host "      请确保:" -ForegroundColor Yellow
    Write-Host "        1. 管控平台forward模块已启动（端口 8067）" -ForegroundColor Gray
    Write-Host "        2. 链路ID已存在（task_chain_config表）" -ForegroundColor Gray
    Write-Host "        3. 发布网关已启动（端口 8090）" -ForegroundColor Gray
    Write-Host "        4. 配置项 gateway.dispatch.auto-dispatch=true" -ForegroundColor Gray
}

Write-Host ""
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "测试完成！" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "工作流程说明:" -ForegroundColor Cyan
Write-Host "  1. 用户在前端点击'确认'按钮" -ForegroundColor Gray
Write-Host "  2. 调用 POST /chain/create/nodes 接口" -ForegroundColor Gray
Write-Host "  3. 创建链路节点到数据库" -ForegroundColor Gray
Write-Host "  4. 【自动触发】调用 PublishGatewayConfigService.deployChainToPublishGateway()" -ForegroundColor Green
Write-Host "  5. 管控平台自动下发配置到发布网关" -ForegroundColor Gray
Write-Host "  6. 发布网关接收配置并启动UDP代理" -ForegroundColor Gray
Write-Host ""
