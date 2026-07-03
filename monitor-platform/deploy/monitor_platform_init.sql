-- MySQL dump 10.13  Distrib 8.0.19, for Win64 (x86_64)
--
-- Host: 192.168.1.31    Database: monitor_platform
-- ------------------------------------------------------
-- Server version	5.7.40

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `alarm_record`
--

DROP TABLE IF EXISTS `alarm_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `alarm_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `alarm_type` varchar(50) NOT NULL COMMENT '告警类型: content_violation-内容违规, device_offline-设备离线, config_fail-配置失败',
  `alarm_level` varchar(20) DEFAULT 'general' COMMENT '告警级别: critical-严重, serious-重要, general-一般, minor-轻微',
  `device_id` varchar(64) DEFAULT NULL COMMENT '设备ID',
  `chain_id` bigint(20) DEFAULT NULL COMMENT '链路ID',
  `board_ip` varchar(50) DEFAULT NULL COMMENT '情报板IP',
  `board_port` int(11) DEFAULT NULL COMMENT '情报板端口',
  `device_name` varchar(100) DEFAULT NULL COMMENT '设备名称',
  `content_id` varchar(64) DEFAULT NULL COMMENT '内容ID（内容违规告警）',
  `violation_type` varchar(100) DEFAULT NULL COMMENT '违规类型',
  `violation_detail` text COMMENT '违规详情/告警详情',
  `alarm_time` datetime NOT NULL COMMENT '告警时间',
  `handle_status` varchar(20) DEFAULT 'pending' COMMENT '处理状态: pending-待处理, processed-已处理, reviewing-复核中',
  `handle_operator` varchar(50) DEFAULT NULL COMMENT '处理人',
  `handle_time` datetime DEFAULT NULL COMMENT '处理时间',
  `handle_remark` varchar(500) DEFAULT NULL COMMENT '处理备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `content_type` varchar(20) DEFAULT NULL COMMENT '违规内容类型',
  `content_data` text COMMENT '违规文本内容（text时填充）',
  `content_file_url` varchar(500) DEFAULT NULL COMMENT '违规内容访问URL（image/video时填充）',
  PRIMARY KEY (`id`),
  KEY `idx_alarm_type` (`alarm_type`),
  KEY `idx_alarm_level` (`alarm_level`),
  KEY `idx_device_id` (`device_id`),
  KEY `idx_content_id` (`content_id`),
  KEY `idx_handle_status` (`handle_status`),
  KEY `idx_alarm_time` (`alarm_time`),
  KEY `idx_board_ip_port` (`board_ip`,`board_port`)
) ENGINE=InnoDB AUTO_INCREMENT=158 DEFAULT CHARSET=utf8mb4 COMMENT='告警记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `alarm_threshold`
--

DROP TABLE IF EXISTS `alarm_threshold`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `alarm_threshold` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `threshold_name` varchar(100) NOT NULL COMMENT '阈值名称',
  `threshold_type` varchar(50) NOT NULL COMMENT '阈值类型: 一级(严重)/二级(严重)/三级(警告)/四级(一般)',
  `threshold_config` text COMMENT '阈值配置(JSON格式)',
  `alert_level` varchar(20) DEFAULT 'general' COMMENT '告警级别: critical/serious/general/minor',
  `status` varchar(20) DEFAULT 'enabled' COMMENT '状态: enabled-启用, disabled-禁用',
  `description` varchar(500) DEFAULT NULL COMMENT '描述',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_threshold_type` (`threshold_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警阈值配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `app_user`
--

DROP TABLE IF EXISTS `app_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `username` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `role_id` bigint(20) DEFAULT NULL,
  `role_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ukey_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'UKey唯一标识',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `employee_no` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工号',
  `post` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '岗位',
  `age` int(11) DEFAULT NULL COMMENT '年龄',
  `gender` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '性别',
  `avatar_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像URL',
  `phone` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
  `is_allow_change` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否允许修改:1允许,0不允许',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  KEY `fk_user_role` (`role_id`),
  CONSTRAINT `fk_user_role` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `device_usage_record`
--

DROP TABLE IF EXISTS `device_usage_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `device_usage_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_id` varchar(100) NOT NULL COMMENT '设备唯一标识',
  `device_type` varchar(50) NOT NULL COMMENT '设备类型',
  `chain_id` bigint(20) NOT NULL COMMENT '占用该设备的链路ID',
  `chain_name` varchar(100) DEFAULT NULL COMMENT '链路名称（冗余字段）',
  `branch_code` varchar(50) DEFAULT NULL COMMENT '分支编码（主节点为NULL，分支节点为B1/B2...）',
  `usage_status` int(11) DEFAULT '1' COMMENT '占用状态: 0-已释放, 1-占用中',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_device_id` (`device_id`),
  KEY `idx_chain_id` (`chain_id`),
  KEY `idx_branch_code` (`branch_code`),
  KEY `idx_usage_status` (`usage_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备占用记录表（支持分支级占用检测）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gateway_dispatch_log`
--

DROP TABLE IF EXISTS `gateway_dispatch_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gateway_dispatch_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `chain_id` bigint(20) NOT NULL COMMENT '链路ID',
  `chain_code` varchar(100) NOT NULL COMMENT '链路编码',
  `branch_code` varchar(50) DEFAULT NULL COMMENT '分支编码（B1/B2/B3...）',
  `gateway_device_id` varchar(100) NOT NULL COMMENT '发布加密网关设备ID',
  `gateway_ip` varchar(50) DEFAULT NULL COMMENT '网关IP地址',
  `gateway_port` int(11) DEFAULT NULL COMMENT '网关端口',
  `config_version` int(11) NOT NULL DEFAULT '1' COMMENT '配置版本号',
  `dispatch_content` text COMMENT '下发内容JSON',
  `dispatch_status` int(11) DEFAULT '0' COMMENT '下发状态',
  `retry_count` int(11) DEFAULT '0' COMMENT '当前重试次数',
  `max_retry` int(11) DEFAULT '3' COMMENT '最大重试次数',
  `next_retry_time` datetime DEFAULT NULL COMMENT '下次重试时间',
  `ack_code` varchar(50) DEFAULT NULL COMMENT '网关回执码',
  `ack_message` varchar(512) DEFAULT NULL COMMENT '网关回执信息',
  `dispatch_time` datetime DEFAULT NULL COMMENT '下发时间',
  `ack_time` datetime DEFAULT NULL COMMENT '确认时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_chain_id` (`chain_id`),
  KEY `idx_chain_branch` (`chain_id`,`branch_code`),
  KEY `idx_gateway` (`gateway_device_id`),
  KEY `idx_status` (`dispatch_status`),
  KEY `idx_dispatch_time` (`dispatch_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网关配置下发记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `permission`
--

DROP TABLE IF EXISTS `permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `permission` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `type` varchar(20) NOT NULL COMMENT '菜单类型: MENU/BUTTON',
  `name` varchar(100) NOT NULL COMMENT '名称',
  `code` varchar(100) NOT NULL COMMENT '标识',
  `route_url` varchar(255) DEFAULT NULL COMMENT '路由地址',
  `plugin_url` varchar(255) DEFAULT NULL COMMENT '组件路径',
  `icon_url` varchar(255) DEFAULT NULL COMMENT '图标路径',
  `sort` int(11) NOT NULL DEFAULT '1' COMMENT '排序',
  `is_enable` varchar(20) NOT NULL DEFAULT 'ENABLED' COMMENT '是否启用: ENABLED/DISABLED',
  `parent_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '父ID, 0表示根节点',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_code` (`code`),
  KEY `idx_permission_parent_sort` (`parent_id`,`sort`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COMMENT='菜单权限表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pin_history`
--

DROP TABLE IF EXISTS `pin_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pin_history` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `cert_serial_no` varchar(128) NOT NULL COMMENT '关联的证书序列号',
  `pin_hash` varchar(255) NOT NULL COMMENT '历史PIN码哈希值',
  `pin_salt` varchar(64) DEFAULT NULL COMMENT 'PIN码盐值',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_cert_serial_no` (`cert_serial_no`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COMMENT='PIN码历史记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `role`
--

DROP TABLE IF EXISTS `role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `role` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'ENABLED',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `permission_codes` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `system_parameters`
--

DROP TABLE IF EXISTS `system_parameters`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_parameters` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模块名称',
  `code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模块编码',
  `parameters` text COLLATE utf8mb4_unicode_ci COMMENT '模块参数(JSON字符串)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_system_parameters_code` (`code`),
  KEY `idx_system_parameters_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统参数配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `t_content_monitor`
--

DROP TABLE IF EXISTS `t_content_monitor`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `t_content_monitor` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `content_id` varchar(128) NOT NULL COMMENT '内容ID（来自发布网关）',
  `gateway_id` varchar(50) DEFAULT NULL COMMENT '网关ID（ruleId）',
  `chain_id` bigint(20) DEFAULT NULL COMMENT '链路ID（业务标识）',
  `protocol` varchar(50) DEFAULT NULL COMMENT '协议类型（如JetFileII-第二种格式）',
  `content_type` varchar(20) NOT NULL COMMENT '内容类型：image-图片，text-文本，binary-二进制',
  `data` longtext COMMENT '内容数据（base64编码或文本）',
  `file_name` varchar(255) DEFAULT NULL COMMENT '文件名',
  `description` varchar(500) DEFAULT NULL COMMENT '描述信息',
  `thumbnail` text COMMENT '缩略图（base64）',
  `source_ip` varchar(50) DEFAULT NULL COMMENT '来源IP（Sigma软件IP）',
  `board_ip` varchar(50) DEFAULT NULL COMMENT '情报板IP（转发目标IP）',
  `board_port` int(11) DEFAULT NULL COMMENT '情报板端口（转发目标端口）',
  `play_batch_id` varchar(128) DEFAULT NULL COMMENT '播放批次ID',
  `play_batch_seq` int(11) DEFAULT NULL COMMENT '播放批次内序号',
  `play_batch_size` int(11) DEFAULT NULL COMMENT '播放批次数量',
  `source_addr` varchar(20) DEFAULT NULL COMMENT 'JetFileII源地址',
  `dest_addr` varchar(50) DEFAULT NULL COMMENT 'JetFileII目标地址',
  `main_cmd` varchar(10) DEFAULT NULL COMMENT 'JetFileII主命令',
  `sub_cmd` varchar(10) DEFAULT NULL COMMENT 'JetFileII子命令',
  `packet_serial` int(11) DEFAULT NULL COMMENT 'JetFileII包序列号',
  `image_format` varchar(10) DEFAULT NULL COMMENT '图片格式（JPEG/PNG/GIF/BMP）',
  `status` varchar(20) DEFAULT 'pending' COMMENT '状态：pending-待识别，normal-正常，violation-违规，stopped-已切断',
  `is_violation` tinyint(4) DEFAULT '0' COMMENT '是否违规：0-否，1-是',
  `violation_type` varchar(100) DEFAULT NULL COMMENT '违规类型',
  `confidence` decimal(5,4) DEFAULT NULL COMMENT '识别置信度（0-1）',
  `keywords` varchar(500) DEFAULT NULL COMMENT '命中的敏感词',
  `reason` varchar(500) DEFAULT NULL COMMENT 'AI判定依据',
  `request_id` varchar(100) DEFAULT NULL COMMENT '阿里云请求追踪ID',
  `error_message` varchar(500) DEFAULT NULL COMMENT '检测失败错误信息',
  `receive_time` datetime DEFAULT NULL COMMENT '接收时间',
  `recognition_time` datetime DEFAULT NULL COMMENT 'AI识别时间',
  `handle_time` datetime DEFAULT NULL COMMENT '处理时间（切断/恢复）',
  `handle_by` varchar(50) DEFAULT NULL COMMENT '处理人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `minio_path` varchar(500) DEFAULT NULL COMMENT 'MinIO对象路径',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_content_id` (`content_id`),
  KEY `idx_gateway_id` (`gateway_id`),
  KEY `idx_chain_id` (`chain_id`),
  KEY `idx_protocol` (`protocol`),
  KEY `idx_content_type` (`content_type`),
  KEY `idx_status` (`status`),
  KEY `idx_is_violation` (`is_violation`),
  KEY `idx_source_ip` (`source_ip`),
  KEY `idx_receive_time` (`receive_time`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_minio_path` (`minio_path`),
  KEY `idx_board_ip_port` (`board_ip`,`board_port`),
  KEY `idx_play_batch` (`play_batch_id`,`play_batch_seq`)
) ENGINE=InnoDB AUTO_INCREMENT=200038 DEFAULT CHARSET=utf8mb4 COMMENT='内容监看表（管控平台-发布网关）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `task_chain_config`
--

DROP TABLE IF EXISTS `task_chain_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `task_chain_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `chain_name` varchar(100) NOT NULL COMMENT '链路名称',
  `chain_code` varchar(100) NOT NULL COMMENT '链路编码（唯一标识）',
  `chain_desc` varchar(500) DEFAULT NULL COMMENT '链路描述',
  `status` int(11) DEFAULT '0' COMMENT '链路状态: 0-离线/不可用, 1-在线/可用, 2-部分可用（可选设备不可用）',
  `enabled` int(11) DEFAULT '1' COMMENT '是否启用: 0-禁用, 1-启用',
  `deleted` int(11) DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  `version` int(11) DEFAULT '1' COMMENT '配置版本号',
  `parent_version_id` bigint(20) DEFAULT NULL COMMENT '父版本ID（用于版本历史追踪）',
  `validation_status` int(11) DEFAULT '0' COMMENT '校验状态: 0-未校验, 1-校验通过, 2-校验失败',
  `validation_message` text COMMENT '校验消息（记录校验失败原因）',
  `last_validation_time` datetime DEFAULT NULL COMMENT '最后校验时间',
  `total_nodes` int(11) DEFAULT '0' COMMENT '总节点数',
  `core_nodes` int(11) DEFAULT '0' COMMENT '核心节点数',
  `optional_nodes` int(11) DEFAULT '0' COMMENT '可选节点数',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` varchar(50) DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(50) DEFAULT NULL COMMENT '更新人',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注信息',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chain_code` (`chain_code`),
  KEY `idx_chain_name` (`chain_name`),
  KEY `idx_status` (`status`),
  KEY `idx_enabled` (`enabled`),
  KEY `idx_deleted` (`deleted`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=41 DEFAULT CHARSET=utf8mb4 COMMENT='任务链路配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `task_chain_node`
--

DROP TABLE IF EXISTS `task_chain_node`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `task_chain_node` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `chain_id` bigint(20) NOT NULL COMMENT '链路ID（外键关联task_chain_config）',
  `device_id` varchar(100) NOT NULL COMMENT '设备唯一标识（关联unified_device_info）',
  `device_name` varchar(100) DEFAULT NULL COMMENT '设备名称（冗余字段）',
  `device_type` varchar(50) NOT NULL COMMENT '设备类型: publish_server(发布服务器), encrypt_gateway(发布加密网关), terminal_encrypt_gateway(终端加密网关), content_server(内容识别服务器), info_board(情报板)',
  `parent_node_id` bigint(20) DEFAULT NULL COMMENT '上游节点ID（数据来源节点，表示时序依赖关系，NULL表示根节点）',
  `branch_code` varchar(50) DEFAULT NULL COMMENT '分支标识（主链路为NULL，终端分支为B1/B2/B3...，用于区分不同的数据分发路径）',
  `branch_name` varchar(100) DEFAULT NULL COMMENT '分支名称（如：北区分支、南区分支）',
  `node_level` int(11) DEFAULT '1' COMMENT '【已废弃】节点层级（平级时序模型中不再使用，保留用于兼容）',
  `is_core` int(11) DEFAULT '1' COMMENT '是否核心节点: 0-可选节点, 1-核心节点',
  `node_status` varchar(20) DEFAULT '离线' COMMENT '节点状态（来自设备状态）: 在线/离线/异常',
  `device_ip` varchar(50) DEFAULT NULL COMMENT '设备IP地址',
  `node_config` text COMMENT '节点配置信息（JSON格式，如IP、端口、特殊参数等）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注信息',
  `parent_id` bigint(20) DEFAULT NULL COMMENT '父级ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chain_device` (`chain_id`,`device_id`),
  KEY `idx_chain_id` (`chain_id`),
  KEY `idx_device_id` (`device_id`),
  KEY `idx_device_type` (`device_type`),
  KEY `idx_parent_node_id` (`parent_node_id`),
  KEY `idx_branch_code` (`branch_code`),
  KEY `idx_is_core` (`is_core`),
  KEY `idx_node_status` (`node_status`)
) ENGINE=InnoDB AUTO_INCREMENT=422 DEFAULT CHARSET=utf8mb4 COMMENT='任务链路节点表（时序依赖树形结构）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ukey_certificate`
--

DROP TABLE IF EXISTS `ukey_certificate`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ukey_certificate` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `cert_serial_no` varchar(128) NOT NULL COMMENT '证书唯一编号（对应 UKey 中的 cerId/cerSn）',
  `certificate_content` text COMMENT '证书内容（PEM/DER 格式，由 SDK 读出后上传）',
  `valid_from` datetime NOT NULL COMMENT '证书生效时间',
  `valid_until` datetime NOT NULL COMMENT '证书过期时间',
  `crypto_algorithm` varchar(32) NOT NULL DEFAULT 'SM2' COMMENT '加密算法（SM2=国密SM2, RSA=RSA2048, SM9=国密SM9）',
  `bound_client_id` varchar(64) DEFAULT NULL COMMENT '绑定的监管客户端ID（如 info-publish-client-001）',
  `cert_status` varchar(20) NOT NULL DEFAULT 'NORMAL' COMMENT '证书状态：NORMAL=正常, REVOKED=注销, LOST=挂失',
  `issuer` varchar(255) DEFAULT NULL COMMENT '证书颁发机构',
  `subject` varchar(255) DEFAULT NULL COMMENT '证书主体信息',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '录入时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `online_status` varchar(16) NOT NULL DEFAULT 'OFFLINE' COMMENT '在线状态：ONLINE=已认证在线, OFFLINE=离线',
  `last_auth_time` datetime DEFAULT NULL COMMENT '最近一次认证成功时间',
  `last_offline_time` datetime DEFAULT NULL COMMENT '最近一次离线时间',
  `display_name` varchar(128) DEFAULT NULL COMMENT 'UKey显示名称（在登录页展示，如"监管站-A号UKey"）',
  `pin_hash` varchar(255) DEFAULT NULL COMMENT 'UKey PIN码的哈希值（SHA-256，用于登录校验）',
  `pin_salt` varchar(64) DEFAULT NULL COMMENT 'PIN码盐值（随机生成，防止彩虹表攻击）',
  `pin_update_time` datetime DEFAULT NULL COMMENT 'PIN码最后更新时间',
  `last_heartbeat_time` datetime DEFAULT NULL COMMENT '客户端最近一次心跳上报时间',
  `client_ip` varchar(64) DEFAULT NULL COMMENT '客户端最近上报的 IP 地址',
  PRIMARY KEY (`id`),
  UNIQUE KEY `cert_serial_no` (`cert_serial_no`),
  KEY `idx_cert_serial_no` (`cert_serial_no`),
  KEY `idx_bound_client_id` (`bound_client_id`),
  KEY `idx_cert_status` (`cert_status`),
  KEY `idx_online_status` (`online_status`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COMMENT='UKey证书管理表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `unified_device_info`
--

DROP TABLE IF EXISTS `unified_device_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `unified_device_info` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_id` varchar(100) NOT NULL COMMENT '设备唯一标识(SN/AppId/CameraId)',
  `device_name` varchar(100) DEFAULT NULL COMMENT '设备名称',
  `device_type` varchar(50) NOT NULL COMMENT '设备种类: publish_gateway（发布端加密网关）/terminal_encrypt_gateway（终端加密网关）/publish_server（信息发布服器）/content_server（内容识别服务器）/情报板（info_board）',
  `ip_address` varchar(50) DEFAULT NULL COMMENT 'IP地址',
  `port` int(11) DEFAULT NULL COMMENT '端口号',
  `mac` varchar(64) DEFAULT NULL COMMENT 'MAC地址（发布网关等设备使用，格式: AA-BB-CC-DD-EE-FF）',
  `location` varchar(100) DEFAULT NULL COMMENT '场景/位置',
  `region_id` bigint(20) DEFAULT NULL COMMENT '所属区域ID',
  `longitude` decimal(10,6) DEFAULT NULL COMMENT '经度',
  `latitude` decimal(10,6) DEFAULT NULL COMMENT '纬度',
  `status` varchar(20) DEFAULT '离线' COMMENT '状态',
  `version` varchar(50) DEFAULT NULL COMMENT '版本号',
  `manufacturer` varchar(100) DEFAULT NULL COMMENT '制造商',
  `model` varchar(100) DEFAULT NULL COMMENT '型号',
  `last_online_time` datetime DEFAULT NULL COMMENT '最后在线/心跳时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `extra_info` text COMMENT '特有属性(JSON格式)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_id` (`device_id`),
  KEY `idx_device_type` (`device_type`),
  KEY `idx_status` (`status`),
  KEY `idx_ip_address` (`ip_address`),
  KEY `idx_unified_device_region_id` (`region_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=62 DEFAULT CHARSET=utf8mb4 COMMENT='统一设备信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gateway_self_test_report`
--

DROP TABLE IF EXISTS `gateway_self_test_report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gateway_self_test_report` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'primary key',
  `device_id` varchar(100) NOT NULL COMMENT 'gateway device id',
  `overall_status` varchar(20) NOT NULL COMMENT 'PASS or FAIL',
  `encrypt_sample_ok` tinyint(1) DEFAULT NULL COMMENT 'encrypt sample check',
  `decrypt_sample_ok` tinyint(1) DEFAULT NULL COMMENT 'decrypt sample check',
  `platform_handshake_ok` tinyint(1) DEFAULT NULL COMMENT 'platform handshake check',
  `ukey_status_ok` tinyint(1) DEFAULT NULL COMMENT 'ukey status check',
  `detail_json` text COMMENT 'self-test detail json',
  `error_message` varchar(1000) DEFAULT NULL COMMENT 'error message',
  `report_time` datetime NOT NULL COMMENT 'gateway report time',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  PRIMARY KEY (`id`),
  KEY `idx_gateway_self_test_device_id` (`device_id`),
  KEY `idx_gateway_self_test_report_time` (`report_time`),
  KEY `idx_gateway_self_test_status` (`overall_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='gateway self-test report';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `gateway_deploy_log`
--

DROP TABLE IF EXISTS `gateway_deploy_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gateway_deploy_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'primary key',
  `device_id` varchar(100) DEFAULT NULL COMMENT 'gateway device id',
  `source_type` varchar(50) NOT NULL COMMENT 'GATEWAY/DEPLOY/DISCOVERY',
  `log_level` varchar(20) NOT NULL COMMENT 'log level',
  `message` varchar(2000) NOT NULL COMMENT 'log message',
  `context_json` text COMMENT 'log context json',
  `client_ip` varchar(64) DEFAULT NULL COMMENT 'client ip',
  `report_time` datetime NOT NULL COMMENT 'log report time',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'create time',
  PRIMARY KEY (`id`),
  KEY `idx_gateway_deploy_log_device_id` (`device_id`),
  KEY `idx_gateway_deploy_log_source` (`source_type`),
  KEY `idx_gateway_deploy_log_level` (`log_level`),
  KEY `idx_gateway_deploy_log_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='gateway deploy log';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping routines for database 'monitor_platform'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-05-08 10:11:12
