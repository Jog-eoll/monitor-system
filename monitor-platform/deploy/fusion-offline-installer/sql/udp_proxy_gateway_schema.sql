-- MySQL dump 10.13  Distrib 5.7.40, for Linux (x86_64)
--
-- Host: localhost    Database: udp_proxy_gateway
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
-- Current Database: `udp_proxy_gateway`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `udp_proxy_gateway` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */;

USE `udp_proxy_gateway`;

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
  `protocol` varchar(8) NOT NULL DEFAULT 'UDP' COMMENT '传输协议：UDP 或 TCP',
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
  `manufacturer` varchar(64) DEFAULT NULL COMMENT '情报板厂家标识',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '逻辑删除（0-未删除，1-已删除）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_rule_id` (`rule_id`) USING BTREE,
  KEY `idx_listen_port` (`listen_port`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_gateway_sn` (`gateway_sn`) USING BTREE,
  KEY `idx_chain_id` (`chain_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=27 DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='UDP代理规则表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping events for database 'udp_proxy_gateway'
--

--
-- Dumping routines for database 'udp_proxy_gateway'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-24 18:28:46
