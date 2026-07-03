-- ========== publish-gateway 数据库初始化脚本 ==========
-- 此脚本在 MySQL 容器首次启动时自动执行

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS udp_proxy_gateway 
  CHARACTER SET utf8mb4 
  COLLATE utf8mb4_unicode_ci;

USE udp_proxy_gateway;

-- ========== UDP 代理规则表 ==========
CREATE TABLE IF NOT EXISTS `udp_proxy_rule` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_id` varchar(128) NOT NULL COMMENT '规则唯一标识',
  `rule_name` varchar(256) DEFAULT NULL COMMENT '规则名称',
  `protocol` varchar(16) DEFAULT 'UDP' COMMENT '协议类型（UDP/TCP）',
  `listen_ip` varchar(64) DEFAULT NULL COMMENT '监听IP，为空表示所有接口',
  `listen_port` int(11) NOT NULL COMMENT '监听端口',
  `source_ip` varchar(64) DEFAULT NULL COMMENT '允许的源IP，为空表示不限制',
  `target_ip` varchar(64) NOT NULL COMMENT '目标IP（情报板）',
  `target_port` int(11) NOT NULL COMMENT '目标端口（情报板）',
  `encrypt_enabled` tinyint(1) DEFAULT '0' COMMENT '是否启用加密转发',
  `terminal_gateway_ip` varchar(64) DEFAULT NULL COMMENT '终端网关IP（加密模式）',
  `terminal_gateway_port` int(11) DEFAULT NULL COMMENT '终端网关端口（加密模式）',
  `status` varchar(32) DEFAULT 'ENABLED' COMMENT '规则状态（ENABLED/DISABLED）',
  `display_board_id` varchar(128) DEFAULT NULL COMMENT '情报板设备ID',
  `gateway_sn` varchar(128) DEFAULT NULL COMMENT '网关序列号',
  `config_source` varchar(32) DEFAULT 'MANUAL' COMMENT '配置来源（MANUAL/PUSH）',
  `config_id` bigint(20) DEFAULT NULL COMMENT '配置ID',
  `chain_id` bigint(20) DEFAULT NULL COMMENT '链路ID',
  `manufacturer` varchar(64) DEFAULT NULL COMMENT '厂商标识（nova/sigma/colorlight）',
  `decrypt_enabled` tinyint(1) DEFAULT '0' COMMENT '是否启用解密',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除标记（0-正常 1-已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_id` (`rule_id`),
  KEY `idx_chain_id` (`chain_id`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='UDP代理规则表';

-- ========== Gateway 分发日志表 ==========
CREATE TABLE IF NOT EXISTS `gateway_dispatch_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `chain_id` bigint(20) DEFAULT NULL COMMENT '链路ID',
  `branch_code` varchar(32) DEFAULT NULL COMMENT '分支代码',
  `gateway_type` varchar(32) DEFAULT NULL COMMENT '网关类型（publish/terminal）',
  `gateway_ip` varchar(64) DEFAULT NULL COMMENT '网关IP',
  `dispatch_url` varchar(512) DEFAULT NULL COMMENT '下发URL',
  `request_body` text DEFAULT NULL COMMENT '请求体',
  `response_body` text DEFAULT NULL COMMENT '响应体',
  `status` varchar(32) DEFAULT 'SUCCESS' COMMENT '状态（SUCCESS/FAILED）',
  `error_message` varchar(1024) DEFAULT NULL COMMENT '错误信息',
  `duration_ms` int(11) DEFAULT NULL COMMENT '耗时（毫秒）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_chain_id` (`chain_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Gateway分发日志表';

-- ========== 设备 MQTT 命令表 ==========
CREATE TABLE IF NOT EXISTS `device_mqtt_command` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `command_id` varchar(128) NOT NULL COMMENT '命令唯一标识',
  `device_id` varchar(128) DEFAULT NULL COMMENT '目标设备ID',
  `device_type` varchar(64) DEFAULT NULL COMMENT '设备类型',
  `command_type` varchar(64) DEFAULT NULL COMMENT '命令类型',
  `command_data` text DEFAULT NULL COMMENT '命令数据（JSON）',
  `status` varchar(32) DEFAULT 'PENDING' COMMENT '状态（PENDING/SENT/ACK/FAILED）',
  `retry_count` int(11) DEFAULT '0' COMMENT '重试次数',
  `max_retries` int(11) DEFAULT '3' COMMENT '最大重试次数',
  `error_message` varchar(1024) DEFAULT NULL COMMENT '错误信息',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_command_id` (`command_id`),
  KEY `idx_device_id` (`device_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备MQTT命令表';

-- 创建应用用户（如果不存在）
-- 注意：实际用户密码通过环境变量传入，此处仅为示例
-- GRANT ALL PRIVILEGES ON udp_proxy_gateway.* TO 'monitor_app'@'%';
-- FLUSH PRIVILEGES;
