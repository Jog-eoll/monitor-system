# 测试脚本：验证解密网关 sourceIp 白名单设置
# 测试目的：确认修改后解密网关的 sourceIp 是加密网关 IP 而非客户端 IP

$PlatformHost = "127.0.0.1"
$PlatformPort = "8067"
$PlatformUrl = "http://${PlatformHost}:${PlatformPort}"

# 解密网关地址（根据实际环境修改）
$TerminalGatewayHost = "192.168.1.26"
$TerminalGatewayPort = "8093"
$TerminalGatewayUrl = "http://${TerminalGatewayHost}:${TerminalGatewayPort}"

# 加密网关地址（根据实际环境修改）
$PublishGatewayHost = "192.168.1.25"
$PublishGatewayPort = "8092"

Write-Host "================================================" -ForegroundColor Cyan
Write-Host "测试 sourceIp 白名单设置" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

# ========== 步骤1：查询当前解密网关的规则配置 ==========
Write-Host "[步骤1] 查询解密网关当前规则配置..." -ForegroundColor Yellow
try {
    # 查询运行中的规则
    $rulesResponse = Invoke-RestMethod -Uri "$TerminalGatewayUrl/udp-proxy/rules/running" -Method Get
    Write-Host "  当前运行中的规则数: $($rulesResponse.data.Count)" -ForegroundColor Gray
    
    if ($rulesResponse.data.Count -gt 0) {
        Write-Host "  现有规则列表:" -ForegroundColor Gray
        foreach ($ruleId in $rulesResponse.data) {
            Write-Host "    - RuleId: $ruleId" -ForegroundColor Gray
        }
    }
} catch {
    Write-Host "  [WARN] 无法查询解密网关规则: $_" -ForegroundColor Yellow
}

Write-Host ""

# ========== 步骤2：创建测试链路节点 ==========
Write-Host "[步骤2] 创建测试链路节点..." -ForegroundColor Yellow

# 构建测试数据（使用实际的设备ID和IP）
$createNodesRequest = @"
{
    "chainId": 9999,
    "rootNodes": [
        {
            "deviceId": "TEST-PUB-SERVER-001",
            "deviceName": "测试发布服务器",
            "deviceType": "publish_server",
            "deviceIp": "192.168.1.100",
            "children": [
                {
                    "deviceId": "TEST-PUB-GW-001",
                    "deviceName": "测试加密网关",
                    "deviceType": "publish_gateway",
                    "deviceIp": "$PublishGatewayHost",
                    "children": [
                        {
                            "deviceId": "TEST-TERM-GW-001",
                            "deviceName": "测试解密网关",
                            "deviceType": "terminal_encrypt_gateway",
                            "deviceIp": "$TerminalGatewayHost",
                            "children": [
                                {
                                    "deviceId": "TEST-INFO-BOARD-001",
                                    "deviceName": "测试情报板",
                                    "deviceType": "info_board",
                                    "deviceIp": "192.168.1.200"
                                }
                            ]
                        }
                    ]
                }
            ]
        }
    ],
    "operatorBy": "test"
}
"@

Write-Host "  测试数据准备完成" -ForegroundColor Gray
Write-Host "  - 客户端IP: 192.168.1.100" -ForegroundColor Gray
Write-Host "  - 加密网关IP: $PublishGatewayHost" -ForegroundColor Gray
Write-Host "  - 解密网关IP: $TerminalGatewayHost" -ForegroundColor Gray
Write-Host ""

# ========== 步骤3：触发配置下发 ==========
Write-Host "[步骤3] 触发配置下发..." -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$PlatformUrl/chain/create/nodes" -Method Post -Body $createNodesRequest -ContentType "application/json"
    
    if ($response.code -eq 200) {
        Write-Host "  [PASS] 链路节点创建成功，配置已自动下发" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] 创建失败: $($response.msg)" -ForegroundColor Red
        Write-Host "  请确保管控平台已启动且链路ID不存在" -ForegroundColor Yellow
    }
} catch {
    Write-Host "  [FAIL] 请求失败: $_" -ForegroundColor Red
    Write-Host "  请确保管控平台forward模块已启动（端口 $PlatformPort）" -ForegroundColor Yellow
}

Write-Host ""

# ========== 步骤4：等待配置生效并查询解密网关规则 ==========
Write-Host "[步骤4] 等待配置生效并查询解密网关规则..." -ForegroundColor Yellow
Start-Sleep -Seconds 3  # 等待配置下发完成

try {
    # 查询解密网关的规则详情
    $rulesResponse = Invoke-RestMethod -Uri "$TerminalGatewayUrl/udp-proxy/rules/running" -Method Get
    Write-Host "  运行中的规则数: $($rulesResponse.data.Count)" -ForegroundColor Gray
    
    if ($rulesResponse.data.Count -gt 0) {
        Write-Host "  规则ID列表:" -ForegroundColor Gray
        foreach ($ruleId in $rulesResponse.data) {
            Write-Host "    - $ruleId" -ForegroundColor Gray
        }
    }
} catch {
    Write-Host "  [WARN] 无法查询解密网关规则: $_" -ForegroundColor Yellow
}

Write-Host ""

# ========== 步骤5：验证结果 ==========
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "验证结果说明" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "请检查以下日志确认 sourceIp 设置是否正确：" -ForegroundColor Yellow
Write-Host ""
Write-Host "1. 管控平台日志（搜索关键词）：" -ForegroundColor White
Write-Host "   - 【终端网关下发】配置源IP白名单（加密网关IP）" -ForegroundColor Gray
Write-Host "   - 期望值: 192.168.1.25 (加密网关IP)" -ForegroundColor Green
Write-Host "   - 错误值: 192.168.1.100 (客户端IP)" -ForegroundColor Red
Write-Host ""
Write-Host "2. 解密网关日志（搜索关键词）：" -ForegroundColor White
Write-Host "   - [terminal-gateway] receive config" -ForegroundColor Gray
Write-Host "   - sourceIp= 应该显示加密网关IP" -ForegroundColor Gray
Write-Host ""
Write-Host "3. 解密网关数据库查询：" -ForegroundColor White
Write-Host "   SELECT rule_id, source_ip FROM udp_proxy_rule WHERE chain_id = 9999 AND deleted = 0;" -ForegroundColor Gray
Write-Host "   - source_ip 字段应该是加密网关IP (192.168.1.25)" -ForegroundColor Green
Write-Host ""
Write-Host "4. 数据包白名单校验日志：" -ForegroundColor White
Write-Host "   - 【终端网关】来源校验通过: XXX.XXX.XXX.XXX 匹配发布网关白名单 [192.168.1.25]" -ForegroundColor Gray
Write-Host ""

Write-Host "================================================" -ForegroundColor Cyan
Write-Host "测试完成！" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
