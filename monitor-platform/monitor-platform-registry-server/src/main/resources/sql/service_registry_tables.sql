CREATE TABLE IF NOT EXISTS `service_instance` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'primary key',
  `client_id` varchar(100) DEFAULT NULL COMMENT 'clientId',
  `service_name` varchar(100) NOT NULL COMMENT 'service name',
  `instance_id` varchar(100) NOT NULL COMMENT 'instance id',
  `host` varchar(100) NOT NULL COMMENT 'host',
  `port` int(11) NOT NULL COMMENT 'port',
  `mac_address` varchar(50) DEFAULT NULL COMMENT 'mac address',
  `device_type` varchar(50) DEFAULT NULL COMMENT 'device type',
  `status` varchar(20) DEFAULT 'UP' COMMENT 'UP/DOWN',
  `register_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'register time',
  `last_heartbeat_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'last heartbeat time',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  `weight` int(11) DEFAULT '1' COMMENT 'weight',
  `cluster_name` varchar(50) DEFAULT 'default' COMMENT 'cluster name',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_instance_id` (`instance_id`),
  UNIQUE KEY `uk_client_id` (`client_id`),
  KEY `idx_service_name` (`service_name`),
  KEY `idx_status` (`status`),
  KEY `idx_mac_address` (`mac_address`),
  KEY `idx_device_type` (`device_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='service instance table';

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
