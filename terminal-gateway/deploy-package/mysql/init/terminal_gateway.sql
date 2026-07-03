-- ========== terminal-gateway 数据库初始化脚本 ==========
-- 此脚本在 MySQL 容器首次启动时自动执行

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS terminal_gateway 
  CHARACTER SET utf8mb4 
  COLLATE utf8mb4_unicode_ci;

USE terminal_gateway;

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
