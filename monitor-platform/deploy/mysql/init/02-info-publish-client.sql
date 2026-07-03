-- Info publish client database initialization.
-- Source baseline: 192.168.1.31/info_publish_client schema, 2026-06-24.
-- Runtime data such as operation_log, channel_config, udp_proxy_rule and private keys are intentionally not copied.

CREATE DATABASE IF NOT EXISTS `info_publish_client` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `info_publish_client`;

DROP TABLE IF EXISTS `channel_config`;
CREATE TABLE `channel_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'primary key',
  `channel_name` varchar(128) DEFAULT NULL COMMENT 'channel name',
  `gateway_ip` varchar(64) DEFAULT NULL COMMENT 'gateway management IP',
  `gateway_api_port` int(11) DEFAULT NULL COMMENT 'gateway management API port',
  `gateway_sn` varchar(128) DEFAULT NULL COMMENT 'gateway serial number',
  `gateway_channel_id` bigint(20) DEFAULT NULL COMMENT 'remote gateway channel ID',
  `forward_listen_port` int(11) DEFAULT NULL COMMENT 'forward listen port',
  `display_gateway_ip` varchar(64) DEFAULT NULL COMMENT 'display-side gateway IP',
  `display_gateway_port` int(11) DEFAULT NULL COMMENT 'display-side gateway port',
  `local_ip` varchar(64) DEFAULT NULL COMMENT 'local IP, optional',
  `active` tinyint(1) DEFAULT '0' COMMENT 'whether this is the active channel',
  `status` int(11) DEFAULT '0' COMMENT 'channel status: 0 stopped, 1 running, 2 abnormal',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `last_used_time` datetime DEFAULT NULL COMMENT 'last used time',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='forward channel config';

DROP TABLE IF EXISTS `operation_log`;
CREATE TABLE `operation_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `event_type` varchar(32) NOT NULL COMMENT 'event type',
  `detail` text COMMENT 'event detail',
  `status` varchar(20) DEFAULT NULL COMMENT 'status: SUCCESS/FAIL',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='operation log';

DROP TABLE IF EXISTS `platform_config`;
CREATE TABLE `platform_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `config_key` varchar(64) NOT NULL COMMENT 'config key',
  `config_value` varchar(512) DEFAULT NULL COMMENT 'config value',
  `description` varchar(255) DEFAULT NULL COMMENT 'description',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='platform config';

INSERT INTO `platform_config` (`config_key`, `config_value`, `description`) VALUES
('platform_url', 'http://127.0.0.1:8063', 'monitor platform UKey service URL'),
('client_id', 'info-publish-client-001', 'client unique ID'),
('server_id', '44010000002000000001', 'server auth ID'),
('server_cert_path', 'certs/server.cer', 'server certificate path'),
('ukey_password', '88888888', 'UKey PIN')
ON DUPLICATE KEY UPDATE
  `config_value` = VALUES(`config_value`),
  `description` = VALUES(`description`),
  `update_time` = CURRENT_TIMESTAMP;

DROP TABLE IF EXISTS `secure_publish_key`;
CREATE TABLE `secure_publish_key` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `key_id` varchar(64) NOT NULL,
  `key_role` varchar(32) NOT NULL DEFAULT 'SIGNER',
  `algorithm` varchar(64) NOT NULL DEFAULT 'SHA256withRSA',
  `private_key_pem` mediumtext,
  `public_key_pem` mediumtext NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_secure_publish_key_lookup` (`key_id`,`key_role`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='secure publish signature key';

DROP TABLE IF EXISTS `udp_proxy_rule`;
CREATE TABLE `udp_proxy_rule` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'primary key',
  `rule_id` varchar(128) NOT NULL COMMENT 'unique rule ID',
  `rule_name` varchar(256) DEFAULT NULL COMMENT 'rule name',
  `listen_ip` varchar(64) DEFAULT NULL COMMENT 'listen IP, empty means all interfaces',
  `listen_port` int(11) NOT NULL COMMENT 'listen port',
  `source_ip` varchar(64) DEFAULT NULL COMMENT 'allowed source IP, empty means all sources',
  `target_ip` varchar(64) NOT NULL COMMENT 'target display board IP',
  `target_port` int(11) NOT NULL COMMENT 'target display board port',
  `encrypt_enabled` tinyint(1) DEFAULT '0' COMMENT 'whether encrypted forwarding is enabled',
  `terminal_gateway_ip` varchar(64) DEFAULT NULL COMMENT 'terminal gateway IP in encrypted mode',
  `terminal_gateway_port` int(11) DEFAULT NULL COMMENT 'terminal gateway port in encrypted mode',
  `status` varchar(32) DEFAULT 'ENABLED' COMMENT 'rule status: ENABLED/DISABLED',
  `display_board_id` varchar(128) DEFAULT NULL COMMENT 'related display board ID',
  `gateway_sn` varchar(128) DEFAULT NULL COMMENT 'gateway serial number',
  `config_source` varchar(32) DEFAULT 'local' COMMENT 'config source: platform/local',
  `config_id` bigint(20) DEFAULT NULL COMMENT 'platform config ID',
  `chain_id` bigint(20) DEFAULT NULL COMMENT 'chain ID',
  `chain_code` varchar(128) DEFAULT NULL COMMENT 'chain code',
  `branch_code` varchar(64) DEFAULT NULL COMMENT 'branch code',
  `branch_name` varchar(256) DEFAULT NULL COMMENT 'branch name',
  `config_version` int(11) DEFAULT '1' COMMENT 'config version',
  `manufacturer` varchar(64) DEFAULT NULL COMMENT 'display board manufacturer, such as sigma or nova',
  `protocol` varchar(10) DEFAULT 'UDP' COMMENT 'transport protocol: UDP/TCP',
  `gateway_mac` varchar(64) DEFAULT NULL COMMENT 'publish gateway MAC address for client ARP binding',
  `remark` varchar(512) DEFAULT NULL COMMENT 'remark',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  `deleted` tinyint(1) DEFAULT '0' COMMENT 'logical delete flag',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_id` (`rule_id`),
  KEY `idx_listen_port` (`listen_port`),
  KEY `idx_status` (`status`),
  KEY `idx_gateway_sn` (`gateway_sn`),
  KEY `idx_chain_id` (`chain_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='UDP proxy rule';
