-- MySQL dump 10.13  Distrib 5.7.40, for Linux (x86_64)
--
-- Host: localhost    Database: info_publish_client
-- ------------------------------------------------------
-- Server version	5.7.40

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `info_publish_client`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `info_publish_client` /*!40100 DEFAULT CHARACTER SET utf8mb4 */;

USE `info_publish_client`;

--
-- Table structure for table `channel_config`
--

DROP TABLE IF EXISTS `channel_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `channel_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `channel_name` varchar(128) DEFAULT NULL COMMENT '通道名称',
  `gateway_ip` varchar(64) DEFAULT NULL COMMENT '加密网关IP',
  `gateway_api_port` int(11) DEFAULT NULL COMMENT '加密网关API端口',
  `gateway_sn` varchar(128) DEFAULT NULL COMMENT '网关序列号',
  `gateway_channel_id` bigint(20) DEFAULT NULL COMMENT '网关上的通道ID',
  `forward_listen_port` int(11) DEFAULT NULL COMMENT '转发监听端口',
  `display_gateway_ip` varchar(64) DEFAULT NULL COMMENT '显控网关IP',
  `display_gateway_port` int(11) DEFAULT NULL COMMENT '显控网关端口',
  `local_ip` varchar(64) DEFAULT NULL COMMENT '本机IP（白名单）',
  `active` tinyint(1) DEFAULT '0' COMMENT '是否为当前激活通道',
  `status` int(11) DEFAULT '0' COMMENT '通道状态：0-已停止，1-运行中，2-异常',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `last_used_time` datetime DEFAULT NULL COMMENT '最后使用时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='转发通道配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `operation_log`
--

DROP TABLE IF EXISTS `operation_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `operation_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `event_type` varchar(32) NOT NULL COMMENT '事件类型：UKEY_INSERT/UKEY_REMOVE/AUTH_SUCCESS/AUTH_FAIL/CHANNEL_CREATE/CHANNEL_STOP/LOGOUT',
  `detail` text COMMENT '事件详情',
  `status` varchar(20) DEFAULT NULL COMMENT '状态：SUCCESS/FAIL',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `platform_config`
--

DROP TABLE IF EXISTS `platform_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `platform_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `config_key` varchar(64) NOT NULL COMMENT '配置键',
  `config_value` varchar(512) DEFAULT NULL COMMENT '配置值',
  `description` varchar(255) DEFAULT NULL COMMENT '配置说明',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `config_key` (`config_key`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COMMENT='平台配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `secure_publish_key`
--

DROP TABLE IF EXISTS `secure_publish_key`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COMMENT='Secure publish demo signature key table';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `udp_proxy_rule`
--

DROP TABLE IF EXISTS `udp_proxy_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `udp_proxy_rule` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_id` varchar(128) NOT NULL COMMENT '规则ID（唯一标识）',
  `rule_name` varchar(256) DEFAULT NULL COMMENT '规则名称',
  `listen_ip` varchar(64) DEFAULT NULL COMMENT '监听IP（为空时监听所有网卡）',
  `listen_port` int(11) NOT NULL COMMENT '监听端口',
  `source_ip` varchar(64) DEFAULT NULL COMMENT '允许接入的源IP（为空则允许所有）',
  `target_ip` varchar(64) NOT NULL COMMENT '转发目标IP（情报板IP）',
  `target_port` int(11) NOT NULL COMMENT '转发目标端口（情报板端口）',
  `encrypt_enabled` tinyint(1) DEFAULT '0' COMMENT '是否启用加密转发（0-否，1-是）',
  `terminal_gateway_ip` varchar(64) DEFAULT NULL COMMENT '终端网关IP（加密模式时使用）',
  `terminal_gateway_port` int(11) DEFAULT NULL COMMENT '终端网关端口（加密模式时使用）',
  `status` varchar(32) DEFAULT 'ENABLED' COMMENT '规则状态：ENABLED/DISABLED',
  `display_board_id` varchar(128) DEFAULT NULL COMMENT '关联的情报板ID',
  `gateway_sn` varchar(128) DEFAULT NULL COMMENT '网关序列号',
  `config_source` varchar(32) DEFAULT 'local' COMMENT '配置来源：platform-管控平台下发/local-本地配置',
  `config_id` bigint(20) DEFAULT NULL COMMENT '管控平台的配置ID',
  `chain_id` bigint(20) DEFAULT NULL COMMENT '链路ID',
  `chain_code` varchar(128) DEFAULT NULL COMMENT '链路编码',
  `branch_code` varchar(64) DEFAULT NULL COMMENT '分支编码',
  `branch_name` varchar(256) DEFAULT NULL COMMENT '分支名称',
  `config_version` int(11) DEFAULT '1' COMMENT '配置版本号',
  `manufacturer` varchar(64) DEFAULT NULL COMMENT '情报板厂家标识（决定协议解析策略，如: sigma, nova）',
  `protocol` varchar(10) DEFAULT 'UDP' COMMENT '传输协议：UDP（默认）或 TCP',
  `gateway_mac` varchar(64) DEFAULT NULL COMMENT '发布网关MAC地址（客户端ARP绑定使用）',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除（0-未删除，1-已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_id` (`rule_id`),
  KEY `idx_listen_port` (`listen_port`),
  KEY `idx_status` (`status`),
  KEY `idx_gateway_sn` (`gateway_sn`),
  KEY `idx_chain_id` (`chain_id`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COMMENT='UDP代理规则表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping events for database 'info_publish_client'
--

--
-- Dumping routines for database 'info_publish_client'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-24 18:28:47
