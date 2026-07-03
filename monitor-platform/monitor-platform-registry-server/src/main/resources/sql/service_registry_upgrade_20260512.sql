-- For existing databases that already have service_instance.
-- Run the ALTER statements only when the column/index does not already exist.

ALTER TABLE `service_instance`
  ADD COLUMN `client_id` varchar(100) DEFAULT NULL COMMENT 'clientId' AFTER `id`;

ALTER TABLE `service_instance`
  ADD UNIQUE KEY `uk_client_id` (`client_id`);

CREATE TABLE IF NOT EXISTS `registry_client_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'primary key',
  `client_id` varchar(100) NOT NULL COMMENT 'clientId',
  `service_name` varchar(100) DEFAULT NULL COMMENT 'service name',
  `config_content` text COMMENT 'json config content',
  `config_version` bigint(20) DEFAULT '1' COMMENT 'config version',
  `enabled` tinyint(1) DEFAULT '1' COMMENT 'enabled flag',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_client_config_client_id` (`client_id`),
  KEY `idx_client_config_service_name` (`service_name`),
  KEY `idx_client_config_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='registry client config table';
