/*
 Navicat Premium Data Transfer

 Source Server         : 192.168.1.25
 Source Server Type    : MySQL
 Source Server Version : 50744
 Source Host           : 192.168.1.25:3306
 Source Schema         : udp_proxy_gateway

 Target Server Type    : MySQL
 Target Server Version : 50744
 File Encoding         : 65001

 Date: 13/05/2026 18:58:35
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for udp_proxy_rule
-- ----------------------------
DROP TABLE IF EXISTS `udp_proxy_rule`;
CREATE TABLE `udp_proxy_rule`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '规则ID（唯一标识）',
  `rule_name` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '规则名称',
  `listen_ip` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '监听IP（为空时监听所有网卡）',
  `listen_port` int(11) NOT NULL COMMENT '监听端口',
  `source_ip` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '允许接入的源IP（为空则允许所有）',
  `target_ip` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '转发目标IP（情报板IP）',
  `target_port` int(11) NOT NULL COMMENT '转发目标端口（情报板端口）',
  `encrypt_enabled` tinyint(1) NULL DEFAULT 0 COMMENT '是否启用加密转发（0-否，1-是）',
  `protocol` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'UDP' COMMENT '传输协议：UDP 或 TCP',
  `terminal_gateway_ip` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '终端网关IP（加密模式时使用）',
  `terminal_gateway_port` int(11) NULL DEFAULT NULL COMMENT '终端网关端口（加密模式时使用）',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'ENABLED' COMMENT '规则状态：ENABLED/DISABLED',
  `display_board_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '关联的情报板ID',
  `gateway_sn` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '网关序列号',
  `config_source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'local' COMMENT '配置来源：platform-管控平台下发/local-本地配置',
  `config_id` bigint(20) NULL DEFAULT NULL COMMENT '管控平台的配置ID',
  `chain_id` bigint(20) NULL DEFAULT NULL COMMENT '链路ID',
  `chain_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '链路编码',
  `branch_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '分支编码',
  `branch_name` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '分支名称',
  `config_version` int(11) NULL DEFAULT 1 COMMENT '配置版本号',
  `manufacturer` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '情报板厂家标识',
  `remark` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) COMMENT '创建时间',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `deleted` tinyint(1) NULL DEFAULT 0 COMMENT '逻辑删除（0-未删除，1-已删除）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_rule_id`(`rule_id`) USING BTREE,
  INDEX `idx_listen_port`(`listen_port`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_gateway_sn`(`gateway_sn`) USING BTREE,
  INDEX `idx_chain_id`(`chain_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 19 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'UDP代理规则表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
