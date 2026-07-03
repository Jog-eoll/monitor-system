-- ============================================================
-- publish-gateway / udp_proxy_gateway 数据库初始化脚本
-- 版本：1.0.0
-- 说明：幂等脚本，可重复执行；CREATE TABLE 使用 IF NOT EXISTS
-- ============================================================

-- 设置客户端连接参数
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
SET time_zone = '+08:00';

-- ------------------------------------------------------------
-- 1. 创建数据库
-- ------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS `udp_proxy_gateway`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE `udp_proxy_gateway`;

-- ------------------------------------------------------------
-- 2. 代理规则表  udp_proxy_rule
--    数据流：消息发布服务器 --[UDP/TCP]--> [listen_ip:listen_port]
--            --> [target_ip:target_port] --> 情报板
--    加密链路：encrypt_enabled=1 时报文先发至
--             terminal_gateway_ip:terminal_gateway_port（终端网关）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `udp_proxy_rule` (

    -- ---- 主键与标识 ------------------------------------------------
    `id`                    BIGINT(20)   NOT NULL AUTO_INCREMENT
                            COMMENT '自增主键',
    `rule_id`               VARCHAR(128) NOT NULL
                            COMMENT '规则业务唯一标识（UUID 或平台下发ID）',
    `rule_name`             VARCHAR(256) DEFAULT NULL
                            COMMENT '规则描述名称',

    -- ---- 监听侧配置 ------------------------------------------------
    `listen_ip`             VARCHAR(64)  DEFAULT NULL
                            COMMENT '网关监听IP；NULL 表示监听所有网卡（0.0.0.0）',
    `listen_port`           INT(11)      NOT NULL
                            COMMENT '网关监听端口，消息发布服务器向此端口发送数据',
    `source_ip`             VARCHAR(64)  DEFAULT NULL
                            COMMENT '允许接入的源端IP白名单；NULL 表示不限制来源',
    `protocol`              VARCHAR(10)  DEFAULT 'UDP'
                            COMMENT '传输协议：UDP（默认）或 TCP',

    -- ---- 转发目标配置 -----------------------------------------------
    `target_ip`             VARCHAR(64)  NOT NULL
                            COMMENT '转发目标设备IP（情报板）',
    `target_port`           INT(11)      NOT NULL
                            COMMENT '转发目标设备端口（情报板端口）',

    -- ---- 国密加密链路配置 --------------------------------------------
    `encrypt_enabled`       TINYINT(1)   DEFAULT 0
                            COMMENT '是否启用国密加密转发：0-否，1-是',
    `terminal_gateway_ip`   VARCHAR(64)  DEFAULT NULL
                            COMMENT '终端网关IP（加密模式下，密文发往此地址）',
    `terminal_gateway_port` INT(11)      DEFAULT NULL
                            COMMENT '终端网关端口（加密模式使用）',

    -- ---- 规则状态 ---------------------------------------------------
    `status`                VARCHAR(32)  DEFAULT 'ENABLED'
                            COMMENT '规则状态：ENABLED-启用 / DISABLED-禁用',
    `deleted`               TINYINT(1)   DEFAULT 0
                            COMMENT '逻辑删除标记：0-有效，1-已删除',

    -- ---- 设备关联信息 -----------------------------------------------
    `display_board_id`      VARCHAR(128) DEFAULT NULL
                            COMMENT '关联情报板设备ID',
    `gateway_sn`            VARCHAR(128) DEFAULT NULL
                            COMMENT '发布网关设备序列号',
    `manufacturer`          VARCHAR(64)  DEFAULT NULL
                            COMMENT '情报板厂家标识，决定协议解析策略：sigma / nova / ledman 等',

    -- ---- 平台下发配置信息 --------------------------------------------
    `config_source`         VARCHAR(32)  DEFAULT 'local'
                            COMMENT '配置来源：platform-管控平台下发 / local-本地手动配置',
    `config_id`             BIGINT(20)   DEFAULT NULL
                            COMMENT '管控平台配置记录ID',
    `config_version`        INT(11)      DEFAULT 1
                            COMMENT '配置版本号，每次平台下发自增',

    -- ---- 链路信息（平台下发） ----------------------------------------
    `chain_id`              BIGINT(20)   DEFAULT NULL
                            COMMENT '管控平台链路ID',
    `chain_code`            VARCHAR(128) DEFAULT NULL
                            COMMENT '链路编码',
    `branch_code`           VARCHAR(64)  DEFAULT NULL
                            COMMENT '分支编码',
    `branch_name`           VARCHAR(256) DEFAULT NULL
                            COMMENT '分支名称',

    -- ---- 其他 -------------------------------------------------------
    `remark`                VARCHAR(512) DEFAULT NULL
                            COMMENT '备注信息',
    `create_time`           DATETIME     DEFAULT CURRENT_TIMESTAMP
                            COMMENT '记录创建时间',
    `update_time`           DATETIME     DEFAULT CURRENT_TIMESTAMP
                                         ON UPDATE CURRENT_TIMESTAMP
                            COMMENT '记录最后更新时间',

    -- ---- 索引 -------------------------------------------------------
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rule_id`       (`rule_id`),
    KEY        `idx_listen_port`  (`listen_port`),
    KEY        `idx_status`       (`status`),
    KEY        `idx_gateway_sn`   (`gateway_sn`),
    KEY        `idx_chain_id`     (`chain_id`)

) ENGINE=InnoDB
  AUTO_INCREMENT=1
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_general_ci
  COMMENT='UDP/TCP 代理转发规则表';

-- ------------------------------------------------------------
-- 3. 初始示例数据（本地调试用，生产环境按需删除此节）
--    INSERT IGNORE：rule_id 已存在时跳过，保证幂等
-- ------------------------------------------------------------
INSERT IGNORE INTO `udp_proxy_rule`
    (`rule_id`,        `rule_name`,
     `listen_ip`,      `listen_port`, `source_ip`, `protocol`,
     `target_ip`,      `target_port`,
     `encrypt_enabled`, `terminal_gateway_ip`, `terminal_gateway_port`,
     `status`,         `display_board_id`, `gateway_sn`,
     `config_source`,  `config_version`, `manufacturer`, `remark`)
VALUES
-- 示例1：明文UDP转发（sigma厂家，9001端口）
('rule-local-001', '示例-明文UDP-Sigma情报板',
 NULL, 9001, NULL, 'UDP',
 '192.168.1.100', 9001,
 0, NULL, NULL,
 'ENABLED', 'board-001', 'GW-SN-00001',
 'local', 1, 'sigma', '本地调试-明文UDP转发'),

-- 示例2：国密加密UDP转发（经终端网关中转）
('rule-local-002', '示例-加密UDP-Sigma情报板',
 NULL, 9002, NULL, 'UDP',
 '192.168.1.101', 9001,
 1, '192.168.1.62', 8900,
 'ENABLED', 'board-002', 'GW-SN-00001',
 'local', 1, 'sigma', '本地调试-国密加密UDP转发'),

-- 示例3：TCP转发（nova大屏，默认禁用）
('rule-local-003', '示例-明文TCP-Nova大屏',
 NULL, 16600, NULL, 'TCP',
 '192.168.1.200', 16600,
 0, NULL, NULL,
 'DISABLED', 'board-003', 'GW-SN-00001',
 'local', 1, 'nova', '本地调试-Nova大屏TCP转发，默认禁用');

SET FOREIGN_KEY_CHECKS = 1;
