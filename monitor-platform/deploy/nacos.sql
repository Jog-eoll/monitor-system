-- MySQL dump 10.13  Distrib 5.7.40, for Linux (x86_64)
--
-- Host: localhost    Database: nacos
-- ------------------------------------------------------
-- Server version	5.7.40

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `config_info`
--

DROP TABLE IF EXISTS `config_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `config_info` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `data_id` varchar(255) COLLATE utf8_bin NOT NULL COMMENT 'data_id',
  `group_id` varchar(128) COLLATE utf8_bin DEFAULT NULL,
  `content` longtext COLLATE utf8_bin NOT NULL COMMENT 'content',
  `md5` varchar(32) COLLATE utf8_bin DEFAULT NULL COMMENT 'md5',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
  `src_user` text COLLATE utf8_bin COMMENT 'source user',
  `src_ip` varchar(50) COLLATE utf8_bin DEFAULT NULL COMMENT 'source ip',
  `app_name` varchar(128) COLLATE utf8_bin DEFAULT NULL,
  `tenant_id` varchar(128) COLLATE utf8_bin DEFAULT '' COMMENT '租户字段',
  `c_desc` varchar(256) COLLATE utf8_bin DEFAULT NULL,
  `c_use` varchar(64) COLLATE utf8_bin DEFAULT NULL,
  `effect` varchar(64) COLLATE utf8_bin DEFAULT NULL,
  `type` varchar(64) COLLATE utf8_bin DEFAULT NULL,
  `c_schema` text COLLATE utf8_bin,
  `encrypted_data_key` text COLLATE utf8_bin NOT NULL COMMENT '秘钥',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_configinfo_datagrouptenant` (`data_id`,`group_id`,`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='config_info';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `config_info`
--

LOCK TABLES `config_info` WRITE;
/*!40000 ALTER TABLE `config_info` DISABLE KEYS */;
INSERT INTO `config_info` VALUES (1,'application-dev.yml','DEFAULT_GROUP','spring:\n  datasource:\n    driver-class-name: com.mysql.jdbc.Driver\n    url: jdbc:mysql://${MYSQL_IP_PORT:db:3306}/monitor_platform?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai\n    username: ${MYSQL_USERNAME:root}\n    password: ${MYSQL_PASSWORD:root123456}\n  redis:\n    host: ${REDIS_HOST:redis}\n    port: ${REDIS_PORT:6379}\n    password: ${REDIS_PASSWORD:123456}\n    database: 0\n    timeout: 3000ms\n    lettuce:\n      pool:\n        max-active: 8\n        max-wait: -1ms\n        max-idle: 8\n\nmybatis-plus:\n  configuration:\n    map-underscore-to-camel-case: true\n    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl\n  global-config:\n    db-config:\n      id-type: auto\n\nfeign:\n  sentinel:\n    enabled: true','eeaa594a4523ca9edf8b7828be7ab40f','2026-03-26 09:37:52','2026-03-26 14:43:22',NULL,'192.168.1.149','','5c87647a-8a17-4adb-94ce-04047ea26d96','','','','yaml','',''),(2,'monitor-gateway-dev.yml','DEFAULT_GROUP','server:\r\n  port: 8060\r\n\r\nspring:\r\n  cloud:\r\n    gateway:\r\n      discovery:\r\n        locator:\r\n          enabled: true\r\n          lower-case-service-id: true\r\n      globalcors:\r\n        cors-configurations:\r\n          \'[/**]\':\r\n            allowedOriginPatterns: \"*\"\r\n            allowedMethods:\r\n              - GET\r\n              - POST\r\n              - PUT\r\n              - DELETE\r\n              - OPTIONS\r\n            allowedHeaders: \"*\"\r\n            allowCredentials: true\r\n            maxAge: 3600\r\n      routes:\r\n        - id: device-service\r\n          uri: lb://monitor-device\r\n          predicates:\r\n            - Path=/device/**\r\n        - id: ukey-auth-service\r\n          uri: lb://monitor-ukey\r\n          predicates:\r\n            - Path=/auth/**\r\n        - id: ukey-cert-service\r\n          uri: lb://monitor-ukey\r\n          predicates:\r\n            - Path=/cert/**\r\n        - id: alarm-service\r\n          uri: lb://monitor-alarm\r\n          predicates:\r\n            - Path=/alarm/**\r\n        - id: content-service\r\n          uri: lb://monitor-content\r\n          predicates:\r\n            - Path=/content/**\r\n        - id: rule-service\r\n          uri: lb://monitor-rule\r\n          predicates:\r\n            - Path=/rule/**\r\n        - id: forward-chain-service\r\n          uri: lb://monitor-forward\r\n          predicates:\r\n            - Path=/chain/**\r\n        - id: forward-service\r\n          uri: lb://monitor-forward\r\n          predicates:\r\n            - Path=/forward/**\r\n        - id: role-service\r\n          uri: lb://monitor-role\r\n          predicates:\r\n            - Path=/role/**\r\n        - id: file-service\r\n          uri: lb://monitor-content\r\n          predicates:\r\n            - Path=/file/**\r\n        - id: ukey-websocket\r\n          uri: lb:ws://monitor-ukey\r\n          predicates:\r\n            - Path=/ws/ukey-status\r\n        - id: alarm-websocket\r\n          uri: lb:ws://monitor-alarm\r\n          predicates:\r\n            - Path=/ws/**\r\n\r\nmanagement:\r\n  endpoints:\r\n    web:\r\n      exposure:\r\n        include: health,info\r\n  endpoint:\r\n    health:\r\n      show-details: always\r\n\r\nlogging:\r\n  level:\r\n    org.springframework.cloud.gateway: INFO\r\n\r\nmonitor:\r\n  ukey:\r\n    url: ${MONITOR_UKEY_URL:http://monitor-ukey:8063}\r\n\r\njwt:\r\n  secret: monitor-platform-jwt-secret-key-2026\r\n  expire-hours: 8','487897abf68addabbf9ab4bf381ee9e5','2026-03-26 09:41:36','2026-03-26 09:41:36',NULL,'192.168.1.149','','5c87647a-8a17-4adb-94ce-04047ea26d96',NULL,NULL,NULL,'yaml',NULL,''),(3,'monitor-device-dev.yml','DEFAULT_GROUP','server:\r\n  port: 8062\r\n\r\nspring:\r\n  servlet:\r\n    multipart:\r\n      max-file-size: 10MB\r\n      max-request-size: 10MB\r\n\r\nmybatis-plus:\r\n  global-config:\r\n    db-config:\r\n      logic-delete-field: deleted\r\n      logic-delete-value: 1\r\n      logic-not-delete-value: 0\r\n\r\nlogging:\r\n  level:\r\n    com.monitorplatform: debug','e884f4e320dc1d9dfab931a18f89e068','2026-03-26 09:42:12','2026-03-26 09:42:12',NULL,'192.168.1.149','','5c87647a-8a17-4adb-94ce-04047ea26d96',NULL,NULL,NULL,'yaml',NULL,''),(4,'monitor-ukey-dev.yml','DEFAULT_GROUP','server:\r\n  port: 8063\r\n\r\nlogging:\r\n  level:\r\n    com.monitorplatform: debug\r\n\r\njwt:\r\n  secret: monitor-platform-jwt-secret-key-2026\r\n  expire-hours: 8\r\n\r\nukey:\r\n  admin:\r\n    username: ${UKEY_ADMIN_USERNAME:}\r\n    password: ${UKEY_ADMIN_PASSWORD:}\r\n\r\nvauth:\r\n  server:\r\n    mode: ${VAUTH_SERVER_MODE:ukey}\r\n    password: ${VAUTH_SERVER_PASSWORD:}\r\n    auth-id: ${VAUTH_SERVER_AUTH_ID:44010100003330003024}\r\n\r\nclient:\r\n  heartbeat:\r\n    timeout-seconds: 15','de4ba616dc348af957bb055cda266403','2026-03-26 09:42:38','2026-03-26 09:42:38',NULL,'192.168.1.149','','5c87647a-8a17-4adb-94ce-04047ea26d96',NULL,NULL,NULL,'yaml',NULL,''),(5,'monitor-alarm-dev.yml','DEFAULT_GROUP','server:\r\n  port: 8064\r\n\r\nlogging:\r\n  level:\r\n    com.monitorplatform: debug\r\n\r\nmonitor:\r\n  device:\r\n    url: ${MONITOR_DEVICE_URL:http://monitor-device:8062}\r\n  forward:\r\n    url: ${MONITOR_FORWARD_URL:http://monitor-forward:8067}\r\n  content:\r\n    url: ${MONITOR_CONTENT_URL:http://monitor-content:8065}\r\n\r\nterminal:\r\n  gateway:\r\n    port: 8093','9b8d292bb879fda6055bc923bb9eac11','2026-03-26 09:43:09','2026-03-26 09:43:09',NULL,'192.168.1.149','','5c87647a-8a17-4adb-94ce-04047ea26d96',NULL,NULL,NULL,'yaml',NULL,''),(6,'monitor-content-dev.yml','DEFAULT_GROUP','server:\r\n  port: 8065\r\n\r\naliyun:\r\n  dashscope:\r\n    api-key: sk-e663759750674d4f96c16b675516a033\r\n    model: qwen-vl-plus\r\n\r\nmonitor:\r\n  rule:\r\n    url: ${MONITOR_RULE_URL:http://monitor-rule:8066}\r\n  alarm:\r\n    url: ${MONITOR_ALARM_URL:http://monitor-alarm:8064}\r\n  device:\r\n    url: ${MONITOR_DEVICE_URL:http://monitor-device:8062}\r\n  forward:\r\n    url: ${MONITOR_FORWARD_URL:http://monitor-forward:8067}\r\n\r\nminio:\r\n  endpoint: ${MINIO_ENDPOINT:http://minio:9000}\r\n  access-key: ${MINIO_ACCESS_KEY:admin}\r\n  secret-key: ${MINIO_SECRET_KEY:admin12345}\r\n  bucket-name: ${MINIO_BUCKET_NAME:monitor-content}\r\n  secure: false\r\n  migration:\r\n    enabled: false\r\n    batch-size: 100\r\n\r\nlogging:\r\n  level:\r\n    com.monitorplatform.content: debug\r\n    com.alibaba.dashscope: debug','d584936184a070ce753f46e8b62f4e4b','2026-03-26 09:43:34','2026-03-26 09:43:34',NULL,'192.168.1.149','','5c87647a-8a17-4adb-94ce-04047ea26d96',NULL,NULL,NULL,'yaml',NULL,''),(7,'monitor-rule-dev.yml','DEFAULT_GROUP','server:\r\n  port: 8066\r\n\r\nmybatis-plus:\r\n  global-config:\r\n    db-config:\r\n      logic-delete-field: deleted\r\n      logic-delete-value: 1\r\n      logic-not-delete-value: 0\r\n\r\nlogging:\r\n  level:\r\n    com.monitorplatform.rule: debug','45b5e8fd484aaf6dda625524ce4e09e7','2026-03-26 09:43:57','2026-03-26 09:43:57',NULL,'192.168.1.149','','5c87647a-8a17-4adb-94ce-04047ea26d96',NULL,NULL,NULL,'yaml',NULL,''),(8,'monitor-forward-dev.yml','DEFAULT_GROUP','server:\r\n  port: 8067\r\n\r\nlogging:\r\n  level:\r\n    com.monitorplatform.forward: debug\r\n  pattern:\r\n    console: \"%d{yyyy-MM-dd HH:mm:ss.SSS, Asia/Shanghai} [%thread] %-5level %logger{50} - %msg%n\"\r\n\r\nforward:\r\n  gateway:\r\n    default-url: http://192.168.1.25:8092\r\n\r\nmonitor:\r\n  device:\r\n    url: ${MONITOR_DEVICE_URL:http://monitor-device:8062}\r\n\r\ngateway:\r\n  dispatch:\r\n    auto-dispatch: true\r\n    port: 9001\r\n    timeout: 5000','8cc1f1989f13ed147908beea01a3ec98','2026-03-26 09:44:17','2026-03-26 09:44:17',NULL,'192.168.1.149','','5c87647a-8a17-4adb-94ce-04047ea26d96',NULL,NULL,NULL,'yaml',NULL,''),(9,'monitor-role-dev.yml','DEFAULT_GROUP','server:\r\n  port: 8068\r\n\r\nmybatis-plus:\r\n  global-config:\r\n    db-config:\r\n      logic-delete-field: deleted\r\n      logic-delete-value: 1\r\n      logic-not-delete-value: 0\r\n\r\nlogging:\r\n  level:\r\n    com.monitorplatform.role: debug\r\n\r\njwt:\r\n  secret: monitor-platform-jwt-secret-key-2026\r\n  expire-hours: 8','a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4','2026-05-08 10:00:00','2026-05-08 10:00:00',NULL,'192.168.1.149','','5c87647a-8a17-4adb-94ce-04047ea26d96',NULL,NULL,NULL,'yaml',NULL,''),(10,'monitor-platform-registry-server-dev.yml','DEFAULT_GROUP','server:\r\n  port: 8069\r\n\r\nlogging:\r\n  level:\r\n    com.monitorplatform.registry: debug\r\n\r\nregistry:\r\n  heartbeat:\r\n    timeout-seconds: 30\r\n    check-interval-seconds: 10','b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5','2026-05-08 10:00:00','2026-05-08 10:00:00',NULL,'192.168.1.149','','5c87647a-8a17-4adb-94ce-04047ea26d96',NULL,NULL,NULL,'yaml',NULL,'');
/*!40000 ALTER TABLE `config_info` ENABLE KEYS */;
UNLOCK TABLES;

-- monitor-log service config and gateway log/upgrade routes.
INSERT INTO `config_info`
(`data_id`, `group_id`, `content`, `md5`, `gmt_create`, `gmt_modified`, `src_user`, `src_ip`, `app_name`, `tenant_id`, `c_desc`, `c_use`, `effect`, `type`, `c_schema`, `encrypted_data_key`)
VALUES
('monitor-log-dev.yml', 'DEFAULT_GROUP', 'server:\r\n  port: 8071\r\n\r\nlogging:\r\n  level:\r\n    com.monitorplatform.log: debug\r\n\r\nlog:\r\n  retention-days: ${LOG_RETENTION_DAYS:90}\r\n  report-token: ${LOG_REPORT_TOKEN:}\r\n  dedup-window-seconds: ${LOG_DEDUP_WINDOW_SECONDS:60}\r\n  timeline-default-days: ${LOG_TIMELINE_DEFAULT_DAYS:7}\r\n  cleanup-cron: ${LOG_CLEANUP_CRON:0 30 2 * * ?}', MD5('server:\r\n  port: 8071\r\n\r\nlogging:\r\n  level:\r\n    com.monitorplatform.log: debug\r\n\r\nlog:\r\n  retention-days: ${LOG_RETENTION_DAYS:90}\r\n  report-token: ${LOG_REPORT_TOKEN:}\r\n  dedup-window-seconds: ${LOG_DEDUP_WINDOW_SECONDS:60}\r\n  timeline-default-days: ${LOG_TIMELINE_DEFAULT_DAYS:7}\r\n  cleanup-cron: ${LOG_CLEANUP_CRON:0 30 2 * * ?}'), NOW(), NOW(), NULL, '192.168.1.149', '', '5c87647a-8a17-4adb-94ce-04047ea26d96', NULL, NULL, NULL, 'yaml', NULL, '')
ON DUPLICATE KEY UPDATE
  `content` = VALUES(`content`),
  `md5` = MD5(VALUES(`content`)),
  `gmt_modified` = NOW(),
  `type` = 'yaml';

UPDATE `config_info`
SET `content` = REPLACE(
        `content`,
        '        - id: forward-service\r\n          uri: lb://monitor-forward\r\n          predicates:\r\n            - Path=/forward/**\r\n',
        '        - id: forward-service\r\n          uri: lb://monitor-forward\r\n          predicates:\r\n            - Path=/forward/**\r\n        - id: upgrade-service\r\n          uri: lb://monitor-forward\r\n          predicates:\r\n            - Path=/upgrade/**\r\n'
    ),
    `gmt_modified` = NOW()
WHERE `data_id` = 'monitor-gateway-dev.yml'
  AND `group_id` = 'DEFAULT_GROUP'
  AND `tenant_id` = '5c87647a-8a17-4adb-94ce-04047ea26d96'
  AND `content` NOT LIKE '%Path=/upgrade/**%';

UPDATE `config_info`
SET `content` = REPLACE(
        `content`,
        '        - id: upgrade-service\r\n          uri: lb://monitor-forward\r\n          predicates:\r\n            - Path=/upgrade/**\r\n',
        '        - id: upgrade-service\r\n          uri: lb://monitor-forward\r\n          predicates:\r\n            - Path=/upgrade/**\r\n        - id: log-service\r\n          uri: lb://monitor-log\r\n          predicates:\r\n            - Path=/log/**\r\n'
    ),
    `gmt_modified` = NOW()
WHERE `data_id` = 'monitor-gateway-dev.yml'
  AND `group_id` = 'DEFAULT_GROUP'
  AND `tenant_id` = '5c87647a-8a17-4adb-94ce-04047ea26d96'
  AND `content` LIKE '%Path=/upgrade/**%'
  AND `content` NOT LIKE '%Path=/log/**%';

UPDATE `config_info`
SET `md5` = MD5(`content`)
WHERE `data_id` = 'monitor-gateway-dev.yml'
  AND `group_id` = 'DEFAULT_GROUP'
  AND `tenant_id` = '5c87647a-8a17-4adb-94ce-04047ea26d96';

--
-- Table structure for table `config_info_aggr`
--

DROP TABLE IF EXISTS `config_info_aggr`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `config_info_aggr` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `data_id` varchar(255) COLLATE utf8_bin NOT NULL COMMENT 'data_id',
  `group_id` varchar(128) COLLATE utf8_bin NOT NULL COMMENT 'group_id',
  `datum_id` varchar(255) COLLATE utf8_bin NOT NULL COMMENT 'datum_id',
  `content` longtext COLLATE utf8_bin NOT NULL COMMENT '内容',
  `gmt_modified` datetime NOT NULL COMMENT '修改时间',
  `app_name` varchar(128) COLLATE utf8_bin DEFAULT NULL,
  `tenant_id` varchar(128) COLLATE utf8_bin DEFAULT '' COMMENT '租户字段',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_configinfoaggr_datagrouptenantdatum` (`data_id`,`group_id`,`tenant_id`,`datum_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='增加租户字段';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `config_info_aggr`
--

LOCK TABLES `config_info_aggr` WRITE;
/*!40000 ALTER TABLE `config_info_aggr` DISABLE KEYS */;
/*!40000 ALTER TABLE `config_info_aggr` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `config_info_beta`
--

DROP TABLE IF EXISTS `config_info_beta`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `config_info_beta` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `data_id` varchar(255) COLLATE utf8_bin NOT NULL COMMENT 'data_id',
  `group_id` varchar(128) COLLATE utf8_bin NOT NULL COMMENT 'group_id',
  `app_name` varchar(128) COLLATE utf8_bin DEFAULT NULL COMMENT 'app_name',
  `content` longtext COLLATE utf8_bin NOT NULL COMMENT 'content',
  `beta_ips` varchar(1024) COLLATE utf8_bin DEFAULT NULL COMMENT 'betaIps',
  `md5` varchar(32) COLLATE utf8_bin DEFAULT NULL COMMENT 'md5',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
  `src_user` text COLLATE utf8_bin COMMENT 'source user',
  `src_ip` varchar(50) COLLATE utf8_bin DEFAULT NULL COMMENT 'source ip',
  `tenant_id` varchar(128) COLLATE utf8_bin DEFAULT '' COMMENT '租户字段',
  `encrypted_data_key` text COLLATE utf8_bin NOT NULL COMMENT '秘钥',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_configinfobeta_datagrouptenant` (`data_id`,`group_id`,`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='config_info_beta';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `config_info_beta`
--

LOCK TABLES `config_info_beta` WRITE;
/*!40000 ALTER TABLE `config_info_beta` DISABLE KEYS */;
/*!40000 ALTER TABLE `config_info_beta` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `config_info_tag`
--

DROP TABLE IF EXISTS `config_info_tag`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `config_info_tag` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `data_id` varchar(255) COLLATE utf8_bin NOT NULL COMMENT 'data_id',
  `group_id` varchar(128) COLLATE utf8_bin NOT NULL COMMENT 'group_id',
  `tenant_id` varchar(128) COLLATE utf8_bin DEFAULT '' COMMENT 'tenant_id',
  `tag_id` varchar(128) COLLATE utf8_bin NOT NULL COMMENT 'tag_id',
  `app_name` varchar(128) COLLATE utf8_bin DEFAULT NULL COMMENT 'app_name',
  `content` longtext COLLATE utf8_bin NOT NULL COMMENT 'content',
  `md5` varchar(32) COLLATE utf8_bin DEFAULT NULL COMMENT 'md5',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
  `src_user` text COLLATE utf8_bin COMMENT 'source user',
  `src_ip` varchar(50) COLLATE utf8_bin DEFAULT NULL COMMENT 'source ip',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_configinfotag_datagrouptenanttag` (`data_id`,`group_id`,`tenant_id`,`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='config_info_tag';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `config_info_tag`
--

LOCK TABLES `config_info_tag` WRITE;
/*!40000 ALTER TABLE `config_info_tag` DISABLE KEYS */;
/*!40000 ALTER TABLE `config_info_tag` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `config_tags_relation`
--

DROP TABLE IF EXISTS `config_tags_relation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `config_tags_relation` (
  `id` bigint(20) NOT NULL COMMENT 'id',
  `tag_name` varchar(128) COLLATE utf8_bin NOT NULL COMMENT 'tag_name',
  `tag_type` varchar(64) COLLATE utf8_bin DEFAULT NULL COMMENT 'tag_type',
  `data_id` varchar(255) COLLATE utf8_bin NOT NULL COMMENT 'data_id',
  `group_id` varchar(128) COLLATE utf8_bin NOT NULL COMMENT 'group_id',
  `tenant_id` varchar(128) COLLATE utf8_bin DEFAULT '' COMMENT 'tenant_id',
  `nid` bigint(20) NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (`nid`),
  UNIQUE KEY `uk_configtagrelation_configidtag` (`id`,`tag_name`,`tag_type`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='config_tag_relation';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `config_tags_relation`
--

LOCK TABLES `config_tags_relation` WRITE;
/*!40000 ALTER TABLE `config_tags_relation` DISABLE KEYS */;
/*!40000 ALTER TABLE `config_tags_relation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `group_capacity`
--

DROP TABLE IF EXISTS `group_capacity`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `group_capacity` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `group_id` varchar(128) COLLATE utf8_bin NOT NULL DEFAULT '' COMMENT 'Group ID，空字符表示整个集群',
  `quota` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '配额，0表示使用默认值',
  `usage` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '使用量',
  `max_size` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '单个配置大小上限，单位为字节，0表示使用默认值',
  `max_aggr_count` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '聚合子配置最大个数，，0表示使用默认值',
  `max_aggr_size` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '单个聚合数据的子配置大小上限，单位为字节，0表示使用默认值',
  `max_history_count` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '最大变更历史数量',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_id` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='集群、各Group容量信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `group_capacity`
--

LOCK TABLES `group_capacity` WRITE;
/*!40000 ALTER TABLE `group_capacity` DISABLE KEYS */;
/*!40000 ALTER TABLE `group_capacity` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `his_config_info`
--

DROP TABLE IF EXISTS `his_config_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `his_config_info` (
  `id` bigint(20) unsigned NOT NULL,
  `nid` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `data_id` varchar(255) COLLATE utf8_bin NOT NULL,
  `group_id` varchar(128) COLLATE utf8_bin NOT NULL,
  `app_name` varchar(128) COLLATE utf8_bin DEFAULT NULL COMMENT 'app_name',
  `content` longtext COLLATE utf8_bin NOT NULL,
  `md5` varchar(32) COLLATE utf8_bin DEFAULT NULL,
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `src_user` text COLLATE utf8_bin,
  `src_ip` varchar(50) COLLATE utf8_bin DEFAULT NULL,
  `op_type` char(10) COLLATE utf8_bin DEFAULT NULL,
  `tenant_id` varchar(128) COLLATE utf8_bin DEFAULT '' COMMENT '租户字段',
  `encrypted_data_key` text COLLATE utf8_bin NOT NULL COMMENT '秘钥',
  PRIMARY KEY (`nid`),
  KEY `idx_gmt_create` (`gmt_create`),
  KEY `idx_gmt_modified` (`gmt_modified`),
  KEY `idx_did` (`data_id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='多租户改造';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `his_config_info`
--

LOCK TABLES `his_config_info` WRITE;
/*!40000 ALTER TABLE `his_config_info` DISABLE KEYS */;
INSERT INTO `his_config_info` VALUES (0,1,'application-dev.yml','DEFAULT_GROUP','','spring:\r\n  datasource:\r\n    driver-class-name: com.mysql.jdbc.Driver\r\n    url: ${SPRING_DATASOURCE_URL:jdbc:mysql://192.168.1.31:3306/monitor_platform?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai}\r\n    username: ${SPRING_DATASOURCE_USERNAME:root}\r\n    password: ${SPRING_DATASOURCE_PASSWORD:root123456}\r\n  redis:\r\n    host: ${SPRING_REDIS_HOST:192.168.1.31}\r\n    port: ${SPRING_REDIS_PORT:6379}\r\n    password: ${SPRING_REDIS_PASSWORD:123456}\r\n    database: 0\r\n    timeout: 3000ms\r\n    lettuce:\r\n      pool:\r\n        max-active: 8\r\n        max-wait: -1ms\r\n        max-idle: 8\r\n\r\nmybatis-plus:\r\n  configuration:\r\n    map-underscore-to-camel-case: true\r\n    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl\r\n  global-config:\r\n    db-config:\r\n      id-type: auto\r\n\r\nfeign:\r\n  sentinel:\r\n    enabled: true','fbc9b2c210f05cdd4a5112d98b406ae8','2026-03-26 09:37:52','2026-03-26 09:37:52',NULL,'192.168.1.149','I','5c87647a-8a17-4adb-94ce-04047ea26d96',''),(0,2,'monitor-gateway-dev.yml','DEFAULT_GROUP','','server:\r\n  port: 8060\r\n\r\nspring:\r\n  cloud:\r\n    gateway:\r\n      discovery:\r\n        locator:\r\n          enabled: true\r\n          lower-case-service-id: true\r\n      globalcors:\r\n        cors-configurations:\r\n          \'[/**]\':\r\n            allowedOriginPatterns: \"*\"\r\n            allowedMethods:\r\n              - GET\r\n              - POST\r\n              - PUT\r\n              - DELETE\r\n              - OPTIONS\r\n            allowedHeaders: \"*\"\r\n            allowCredentials: true\r\n            maxAge: 3600\r\n      routes:\r\n        - id: device-service\r\n          uri: lb://monitor-device\r\n          predicates:\r\n            - Path=/device/**\r\n        - id: ukey-auth-service\r\n          uri: lb://monitor-ukey\r\n          predicates:\r\n            - Path=/auth/**\r\n        - id: ukey-cert-service\r\n          uri: lb://monitor-ukey\r\n          predicates:\r\n            - Path=/cert/**\r\n        - id: alarm-service\r\n          uri: lb://monitor-alarm\r\n          predicates:\r\n            - Path=/alarm/**\r\n        - id: content-service\r\n          uri: lb://monitor-content\r\n          predicates:\r\n            - Path=/content/**\r\n        - id: rule-service\r\n          uri: lb://monitor-rule\r\n          predicates:\r\n            - Path=/rule/**\r\n        - id: forward-chain-service\r\n          uri: lb://monitor-forward\r\n          predicates:\r\n            - Path=/chain/**\r\n        - id: forward-service\r\n          uri: lb://monitor-forward\r\n          predicates:\r\n            - Path=/forward/**\r\n        - id: file-service\r\n          uri: lb://monitor-content\r\n          predicates:\r\n            - Path=/file/**\r\n        - id: ukey-websocket\r\n          uri: lb:ws://monitor-ukey\r\n          predicates:\r\n            - Path=/ws/ukey-status\r\n        - id: alarm-websocket\r\n          uri: lb:ws://monitor-alarm\r\n          predicates:\r\n            - Path=/ws/**\r\n\r\nmanagement:\r\n  endpoints:\r\n    web:\r\n      exposure:\r\n        include: health,info\r\n  endpoint:\r\n    health:\r\n      show-details: always\r\n\r\nlogging:\r\n  level:\r\n    org.springframework.cloud.gateway: INFO\r\n\r\nmonitor:\r\n  ukey:\r\n    url: ${MONITOR_UKEY_URL:http://monitor-ukey:8063}\r\n\r\njwt:\r\n  secret: monitor-platform-jwt-secret-key-2026\r\n  expire-hours: 8','487897abf68addabbf9ab4bf381ee9e5','2026-03-26 09:41:36','2026-03-26 09:41:36',NULL,'192.168.1.149','I','5c87647a-8a17-4adb-94ce-04047ea26d96',''),(0,3,'monitor-device-dev.yml','DEFAULT_GROUP','','server:\r\n  port: 8062\r\n\r\nspring:\r\n  servlet:\r\n    multipart:\r\n      max-file-size: 10MB\r\n      max-request-size: 10MB\r\n\r\nmybatis-plus:\r\n  global-config:\r\n    db-config:\r\n      logic-delete-field: deleted\r\n      logic-delete-value: 1\r\n      logic-not-delete-value: 0\r\n\r\nlogging:\r\n  level:\r\n    com.monitorplatform: debug','e884f4e320dc1d9dfab931a18f89e068','2026-03-26 09:42:11','2026-03-26 09:42:12',NULL,'192.168.1.149','I','5c87647a-8a17-4adb-94ce-04047ea26d96',''),(0,4,'monitor-ukey-dev.yml','DEFAULT_GROUP','','server:\r\n  port: 8063\r\n\r\nlogging:\r\n  level:\r\n    com.monitorplatform: debug\r\n\r\njwt:\r\n  secret: monitor-platform-jwt-secret-key-2026\r\n  expire-hours: 8\r\n\r\nukey:\r\n  admin:\r\n    username: ${UKEY_ADMIN_USERNAME:}\r\n    password: ${UKEY_ADMIN_PASSWORD:}\r\n\r\nvauth:\r\n  server:\r\n    mode: ${VAUTH_SERVER_MODE:ukey}\r\n    password: ${VAUTH_SERVER_PASSWORD:}\r\n    auth-id: ${VAUTH_SERVER_AUTH_ID:44010100003330003024}\r\n\r\nclient:\r\n  heartbeat:\r\n    timeout-seconds: 15','de4ba616dc348af957bb055cda266403','2026-03-26 09:42:38','2026-03-26 09:42:38',NULL,'192.168.1.149','I','5c87647a-8a17-4adb-94ce-04047ea26d96',''),(0,5,'monitor-alarm-dev.yml','DEFAULT_GROUP','','server:\r\n  port: 8064\r\n\r\nlogging:\r\n  level:\r\n    com.monitorplatform: debug\r\n\r\nmonitor:\r\n  device:\r\n    url: ${MONITOR_DEVICE_URL:http://monitor-device:8062}\r\n  forward:\r\n    url: ${MONITOR_FORWARD_URL:http://monitor-forward:8067}\r\n  content:\r\n    url: ${MONITOR_CONTENT_URL:http://monitor-content:8065}\r\n\r\nterminal:\r\n  gateway:\r\n    port: 8093','9b8d292bb879fda6055bc923bb9eac11','2026-03-26 09:43:09','2026-03-26 09:43:09',NULL,'192.168.1.149','I','5c87647a-8a17-4adb-94ce-04047ea26d96',''),(0,6,'monitor-content-dev.yml','DEFAULT_GROUP','','server:\r\n  port: 8065\r\n\r\naliyun:\r\n  dashscope:\r\n    api-key: sk-e663759750674d4f96c16b675516a033\r\n    model: qwen-vl-plus\r\n\r\nmonitor:\r\n  rule:\r\n    url: ${MONITOR_RULE_URL:http://monitor-rule:8066}\r\n  alarm:\r\n    url: ${MONITOR_ALARM_URL:http://monitor-alarm:8064}\r\n  device:\r\n    url: ${MONITOR_DEVICE_URL:http://monitor-device:8062}\r\n  forward:\r\n    url: ${MONITOR_FORWARD_URL:http://monitor-forward:8067}\r\n\r\nminio:\r\n  endpoint: ${MINIO_ENDPOINT:http://minio:9000}\r\n  access-key: ${MINIO_ACCESS_KEY:admin}\r\n  secret-key: ${MINIO_SECRET_KEY:admin12345}\r\n  bucket-name: ${MINIO_BUCKET_NAME:monitor-content}\r\n  secure: false\r\n  migration:\r\n    enabled: false\r\n    batch-size: 100\r\n\r\nlogging:\r\n  level:\r\n    com.monitorplatform.content: debug\r\n    com.alibaba.dashscope: debug','d584936184a070ce753f46e8b62f4e4b','2026-03-26 09:43:33','2026-03-26 09:43:34',NULL,'192.168.1.149','I','5c87647a-8a17-4adb-94ce-04047ea26d96',''),(0,7,'monitor-rule-dev.yml','DEFAULT_GROUP','','server:\r\n  port: 8066\r\n\r\nmybatis-plus:\r\n  global-config:\r\n    db-config:\r\n      logic-delete-field: deleted\r\n      logic-delete-value: 1\r\n      logic-not-delete-value: 0\r\n\r\nlogging:\r\n  level:\r\n    com.monitorplatform.rule: debug','45b5e8fd484aaf6dda625524ce4e09e7','2026-03-26 09:43:56','2026-03-26 09:43:57',NULL,'192.168.1.149','I','5c87647a-8a17-4adb-94ce-04047ea26d96',''),(0,8,'monitor-forward-dev.yml','DEFAULT_GROUP','','server:\r\n  port: 8067\r\n\r\nlogging:\r\n  level:\r\n    com.monitorplatform.forward: debug\r\n  pattern:\r\n    console: \"%d{yyyy-MM-dd HH:mm:ss.SSS, Asia/Shanghai} [%thread] %-5level %logger{50} - %msg%n\"\r\n\r\nforward:\r\n  gateway:\r\n    default-url: http://192.168.1.25:8092\r\n\r\nmonitor:\r\n  device:\r\n    url: ${MONITOR_DEVICE_URL:http://monitor-device:8062}\r\n\r\ngateway:\r\n  dispatch:\r\n    auto-dispatch: true\r\n    port: 9001\r\n    timeout: 5000','8cc1f1989f13ed147908beea01a3ec98','2026-03-26 09:44:16','2026-03-26 09:44:17',NULL,'192.168.1.149','I','5c87647a-8a17-4adb-94ce-04047ea26d96',''),(1,9,'application-dev.yml','DEFAULT_GROUP','','spring:\r\n  datasource:\r\n    driver-class-name: com.mysql.jdbc.Driver\r\n    url: ${SPRING_DATASOURCE_URL:jdbc:mysql://192.168.1.31:3306/monitor_platform?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai}\r\n    username: ${SPRING_DATASOURCE_USERNAME:root}\r\n    password: ${SPRING_DATASOURCE_PASSWORD:root123456}\r\n  redis:\r\n    host: ${SPRING_REDIS_HOST:192.168.1.31}\r\n    port: ${SPRING_REDIS_PORT:6379}\r\n    password: ${SPRING_REDIS_PASSWORD:123456}\r\n    database: 0\r\n    timeout: 3000ms\r\n    lettuce:\r\n      pool:\r\n        max-active: 8\r\n        max-wait: -1ms\r\n        max-idle: 8\r\n\r\nmybatis-plus:\r\n  configuration:\r\n    map-underscore-to-camel-case: true\r\n    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl\r\n  global-config:\r\n    db-config:\r\n      id-type: auto\r\n\r\nfeign:\r\n  sentinel:\r\n    enabled: true','fbc9b2c210f05cdd4a5112d98b406ae8','2026-03-26 14:28:52','2026-03-26 14:28:51',NULL,'192.168.1.204','U','5c87647a-8a17-4adb-94ce-04047ea26d96',''),(1,10,'application-dev.yml','DEFAULT_GROUP','','spring:\n  datasource:\n    driver-class-name: com.mysql.jdbc.Driver\n    url: jdbc:mysql://${MYSQL_IP_PORT}/monitor_platform?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai\n    username: ${MYSQL_USERNAME:root}\n    password: ${MYSQL_PASSWORD:root123456}\n  redis:\n    host: ${REDIS_HOST:redis}\n    port: ${REDIS_PORT:6379}\n    password: ${REDIS_PASSWORD:123456}\n    database: 0\n    timeout: 3000ms\n    lettuce:\n      pool:\n        max-active: 8\n        max-wait: -1ms\n        max-idle: 8\n\nmybatis-plus:\n  configuration:\n    map-underscore-to-camel-case: true\n    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl\n  global-config:\n    db-config:\n      id-type: auto\n\nfeign:\n  sentinel:\n    enabled: true','4febde09e9e8f58b9fef68203ad2d9cd','2026-03-26 14:43:21','2026-03-26 14:43:22',NULL,'192.168.1.149','U','5c87647a-8a17-4adb-94ce-04047ea26d96','');
/*!40000 ALTER TABLE `his_config_info` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `permissions`
--

DROP TABLE IF EXISTS `permissions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `permissions` (
  `role` varchar(50) NOT NULL,
  `resource` varchar(255) NOT NULL,
  `action` varchar(8) NOT NULL,
  UNIQUE KEY `uk_role_permission` (`role`,`resource`,`action`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `permissions`
--

LOCK TABLES `permissions` WRITE;
/*!40000 ALTER TABLE `permissions` DISABLE KEYS */;
/*!40000 ALTER TABLE `permissions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `roles`
--

DROP TABLE IF EXISTS `roles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `roles` (
  `username` varchar(50) NOT NULL,
  `role` varchar(50) NOT NULL,
  UNIQUE KEY `idx_user_role` (`username`,`role`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `roles`
--

LOCK TABLES `roles` WRITE;
/*!40000 ALTER TABLE `roles` DISABLE KEYS */;
INSERT INTO `roles` VALUES ('nacos','ROLE_ADMIN');
/*!40000 ALTER TABLE `roles` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tenant_capacity`
--

DROP TABLE IF EXISTS `tenant_capacity`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `tenant_capacity` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id` varchar(128) COLLATE utf8_bin NOT NULL DEFAULT '' COMMENT 'Tenant ID',
  `quota` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '配额，0表示使用默认值',
  `usage` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '使用量',
  `max_size` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '单个配置大小上限，单位为字节，0表示使用默认值',
  `max_aggr_count` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '聚合子配置最大个数',
  `max_aggr_size` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '单个聚合数据的子配置大小上限，单位为字节，0表示使用默认值',
  `max_history_count` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '最大变更历史数量',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='租户容量信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tenant_capacity`
--

LOCK TABLES `tenant_capacity` WRITE;
/*!40000 ALTER TABLE `tenant_capacity` DISABLE KEYS */;
/*!40000 ALTER TABLE `tenant_capacity` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tenant_info`
--

DROP TABLE IF EXISTS `tenant_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `tenant_info` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `kp` varchar(128) COLLATE utf8_bin NOT NULL COMMENT 'kp',
  `tenant_id` varchar(128) COLLATE utf8_bin DEFAULT '' COMMENT 'tenant_id',
  `tenant_name` varchar(128) COLLATE utf8_bin DEFAULT '' COMMENT 'tenant_name',
  `tenant_desc` varchar(256) COLLATE utf8_bin DEFAULT NULL COMMENT 'tenant_desc',
  `create_source` varchar(32) COLLATE utf8_bin DEFAULT NULL COMMENT 'create_source',
  `gmt_create` bigint(20) NOT NULL COMMENT '创建时间',
  `gmt_modified` bigint(20) NOT NULL COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_info_kptenantid` (`kp`,`tenant_id`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='tenant_info';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tenant_info`
--

LOCK TABLES `tenant_info` WRITE;
/*!40000 ALTER TABLE `tenant_info` DISABLE KEYS */;
INSERT INTO `tenant_info` VALUES (1,'1','5c87647a-8a17-4adb-94ce-04047ea26d96','monitor-platform-dev','管控平台 开发环境','nacos',1773911387283,1774257563917),(2,'1','6528df93-8f23-40c7-b96b-ee3bf8d18492','monitor-platform-test','测试环境','nacos',1773911603405,1773911603405),(3,'1','8553dd24-2ac9-4b22-b64f-1b770127c204','monitor-platform-prod','生产环境','nacos',1773911681675,1774257553472);
/*!40000 ALTER TABLE `tenant_info` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `users` (
  `username` varchar(50) NOT NULL,
  `password` varchar(500) NOT NULL,
  `enabled` tinyint(1) NOT NULL,
  PRIMARY KEY (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES ('nacos','$2a$10$EuWPZHzz32dJN7jexM34MOeYirDdFAZm2kuWj7VEOJhhZkDrxfvUu',1);
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-03-26 17:25:20
