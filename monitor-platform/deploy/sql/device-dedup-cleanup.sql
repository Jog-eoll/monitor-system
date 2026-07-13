-- ========== 设备重复注册治理 - 一次性清理 SQL ==========
-- 针对问题：terminal-udp-gateway-192.168.77.12-8093 与 terminal-gateway-12 重复
-- 原因：网关在不同配置下生成了不同 instanceId/deviceId，但指向同一物理设备（deviceType+host+port 相同）
-- 执行前请先备份 device 表，并根据实际表名/字段名调整

-- 1. 查询重复设备记录（按 device_type + host + port 分组）
SELECT id, instance_id, device_type, host, port, status, create_time, update_time
FROM device
WHERE (device_type, host, port) IN (
    SELECT device_type, host, port
    FROM device
    GROUP BY device_type, host, port
    HAVING COUNT(*) > 1
)
ORDER BY device_type, host, port, create_time DESC;

-- 2. 标记旧记录为离线（保留每组最新 create_time 的记录，其余标记离线）
UPDATE device d
INNER JOIN (
    SELECT device_type, host, port, MAX(create_time) AS latest_time
    FROM device
    GROUP BY device_type, host, port
    HAVING COUNT(*) > 1
) latest ON d.device_type = latest.device_type
    AND d.host = latest.host
    AND d.port = latest.port
    AND d.create_time < latest.latest_time
SET d.status = '离线', d.update_time = NOW();

-- 3. 验证清理结果（确认每组只剩一个在线记录）
SELECT device_type, host, port, COUNT(*) AS cnt, GROUP_CONCAT(instance_id) AS instances
FROM device
GROUP BY device_type, host, port
HAVING COUNT(*) > 1;

-- ========== 生产配置建议 ==========
-- 1. 固定使用 MQTT_DEVICE_ID 环境变量，避免网关重启后生成不同 deviceId
-- 2. 固定使用 REGISTRY_CLIENT_ID，避免注册服务生成不同 instanceId
-- 3. 平台 MqttRegisterHandler 已在注册 body 中加入 dedupKey（deviceType:host:port），
--    后端 register 接口可据此检查旧记录并标记离线
