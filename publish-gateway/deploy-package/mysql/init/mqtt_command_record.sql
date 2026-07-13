-- ========== MQTT 命令幂等记录表 ==========
-- 用于网关命令幂等持久化，防止容器重启后重复 messageId 重复执行副作用
-- 此脚本在 MySQL 容器首次启动时自动执行

USE udp_proxy_gateway;

CREATE TABLE IF NOT EXISTS `mqtt_command_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `message_id` varchar(64) NOT NULL COMMENT 'MQTT messageId',
  `command` varchar(64) DEFAULT NULL COMMENT '命令名',
  `first_seen_at` datetime NOT NULL COMMENT '首次接收时间',
  `expire_at` datetime NOT NULL COMMENT '过期时间',
  `status` varchar(32) DEFAULT 'PROCESSING' COMMENT '状态（PROCESSING/SUCCESS/FAILED/SKIPPED）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_id` (`message_id`),
  KEY `idx_expire_at` (`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MQTT命令幂等记录';
